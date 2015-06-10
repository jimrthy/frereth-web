(ns com.frereth.web.routes.core
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [fnhouse.docs :as docs]
            [fnhouse.handlers :as handlers]
            [fnhouse.routes :as routes]
            [com.frereth.common.util :as util]
            #_[com.frereth.client.system :as client]
            ;; TODO: switch to the frereth.client instead
            [frereth-server.comms :as comms]
            [ribol.core :refer [raise]]
            ;; I have dependencies on several other ring wrappers.
            ;; Esp. anti-forgery, defaults, and headers.
            ;; TODO: Make use of those
            [ring.middleware.content-type :refer (wrap-content-type)]
            [ring.middleware.format :refer (wrap-restful-format)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.middleware.stacktrace :refer (wrap-stacktrace)]
            [ring.util.response :as response]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang IPersistentMap ISeq]
           [frereth_server.comms Connection]
           #_[com.frereth.client.communicator ServerSocket]
           [java.io File InputStream]
           [java.security.cert X509Certificate]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def StandardDescription {})
(def StandardCtorDescription {:http-router StandardDescription})
(def WebSocketDescription {})

(declare return-index wrapped-root-handler)
;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord HttpRoutes [frereth-client :- Connection
                         ;; This is a function that takes 1
                         ;; arg and returns a Ring response.
                         ;; TODO: Spec that schema
                         http-router]
  component/Lifecycle
  (start
   [this]
   (assoc this :http-router (wrapped-root-handler this)))
  (stop
   [this]
   (assoc this :http-router nil)))

(def UnstartedHttpRoutes (assoc StandardDescription
                                :http-router s/Any
                                :frereth-server s/Any))

(def HttpRouteMap
  "Map of route prefix to the namespace where the handler should live"
  {s/Str s/Symbol})

(def RingRequest
  "What the spec says we shall use"
  {:server-port s/Int
   :server-name s/Str
   :remote-addr s/Str
   :uri s/Str
   ;; From the Ring spec
   (s/optional-key :query-string) s/Str
   ;; This is actually added through middleware, but fnhouse
   ;; requires it
   :query-params {s/Any s/Any}
   :scheme (s/enum :http :https)
   :request-method (s/enum :get :head :options :put :post :delete)
   (s/optional-key :content-type) s/Str
   (s/optional-key :content-length) s/Int
   (s/optional-key :character-encoding) s/Str
   (s/optional-key :ssl-client-cert) X509Certificate
   :header {s/Any s/Any}
   (s/optional-key :body) InputStream})

(def LegalRingResponseBody  (s/either s/Str [s/Any] File InputStream IPersistentMap ISeq))

(s/defschema RingResponse {:status s/Int
                           :headers {s/Any s/Any}
                           (s/optional-key :body) LegalRingResponseBody})

(def HttpRequestHandler (s/=> RingResponse RingRequest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn return-index
  "Because there are a ton of different ways to try to access the root of a web app"
  []
  (if-let [url (io/resource "public/index.html")]
    ;; TODO: Return the stream rather than loading everything into memory here.
    ;; Then again, it isn't like "everything" is all that much.
    ;; But I've had poor experiences in the past with that resource disappearing
    ;; when we deploy the .war file.
    ;; That's something to watch out for, but, honestly, it makes more sense for
    ;; nginx to serve up the static files anyway.
    {:body (slurp url)
     :status 200
     :headers {"Content-Type" "text/html"}}
    {:body "<!DOCTYPE html>
<html>
  <head>
    <title>Oops</title>
  </head>
  <body>
    <h1>Missing Index</h1>
    <p>Odds are, there's something wrong with the way you built your .war file</p>
  </body>
</html>"
     :status 404
     :headers {"Content-Type" "text/html"}}))

(defn index-middleware
  [handler]
  (fn [req]
    (let [path (:uri req)]
      (log/debug "Requested:" path)
      (if (= path "/")
        ;; Need to add ring-anti-forgery around this
        ;; Q: Is there any real point, considering the basic
        ;; architecture I have in mind? It really isn't
        ;; for REST calls, and this is pretty much the only
        ;; "standard" HTTP request I expect to handle.
        ;; Actually, I probably shouldn't even handle this
        ;; one. The same resource handler that's getting my
        ;; cljs should be able to cope with this.
        (return-index)
        (handler req)))))

(s/defn debug-middleware :- HttpRequestHandler
  "Log the request as it comes in.

TODO: Should probably save it so we can examine later"
  [handler :- HttpRequestHandler]
  (s/fn :- RingResponse
    [req :- RingRequest]
    (log/debug "Incoming Request:\n" (util/pretty req))
    (handler req)))

(defn wrap-standard-middleware
  "Build the call stack of everything the standard handlers go through.

  This seems to inevitably turn into a big, hairy, nasty, tangled-up mess.

  Take the time to learn and appreciate everything that's going on here."
  [handler]
  (-> handler
      debug-middleware  ; TODO: Only in debug mode
      wrap-stacktrace   ; TODO: Also just in debug mode
      (wrap-restful-format :formats [:edn :json-kw :yaml-kw :transit-json :transit-msgpack])
      wrap-params  ; Q: How does this interact w/ wrap-restful-format?
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(s/defn attach-docs
  "This is really where the fnhouse magic happens
TODO: Spec return type"
  [component :- HttpRoutes
   route-map :- HttpRouteMap]
  (let [proto-handlers (handlers/nss->proto-handlers route-map)
        all-docs (docs/all-docs (map :info proto-handlers))]
    (-> component
        (assoc :api-docs all-docs)
        ((handlers/curry-resources proto-handlers)))))

(s/defn fnhouse-handling
  "Convert the fnhouse routes defined in route-map to actual route handlers,
making the component available as needed"
  [component :- HttpRoutes
   route-map :- HttpRouteMap]
  (let [routes-with-documentation (attach-docs component route-map)
        ;; TODO: if there's middleware to do coercion, add it here
        ]
    (routes/root-handler routes-with-documentation)))

(s/defn wrapped-root-handler
  "Returns a handler (with middleware) for 'normal' http requests"
  [component :- HttpRoutes]
  (-> (fnhouse-handling component {"v1" 'frereth-web.routes.v1})
      index-middleware
      wrap-standard-middleware))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- UnstartedHttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
