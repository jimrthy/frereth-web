(ns com.frereth.web.routes.core
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [com.frereth.common.util :as util]
            [com.frereth.client.communicator :as comms]
            [com.frereth.web.routes.ring :as fr-ring]
            [com.frereth.web.routes.v1]  ; Just so it gets compiled, so fnhouse can find it
            [com.frereth.web.routes.websock :as ws]
            [com.stuartsierra.component :as component]
            [fnhouse.docs :as docs]
            [fnhouse.handlers :as handlers]
            [fnhouse.routes :as routes]
            [fnhouse.schemas :as fn-schemas]
            [ribol.core :refer [raise]]
            ;; Q: Am I getting the ring header middleware with this group?
            [ring.middleware.content-type :refer (wrap-content-type)]
            [ring.middleware.defaults]
            [ring.middleware.format :refer (wrap-restful-format)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.middleware.stacktrace :refer (wrap-stacktrace)]
            [ring.util.response :as response]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.frereth.client.communicator ServerSocket]
           [com.frereth.web.routes.websock WebSockHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def StandardDescription {})
(def StandardCtorDescription {:http-router StandardDescription})

(declare wrapped-root-handler)
;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord HttpRoutes [frereth-client :- (s/maybe ServerSocket)
                         ;; This record winds up in the system as
                         ;; the http-router. It's confusing for it
                         ;; to have another.
                         ;; TODO: change one name or the other
                         http-router :- (s/maybe fr-ring/HttpRequestHandler)
                         web-sock-handler :- (s/maybe WebSockHandler)]
  component/Lifecycle
  (start
   [this]
   (assert web-sock-handler)
   (assoc this
          :http-router (wrapped-root-handler this)))
  (stop
   [this]
   (log/debug "HTTP Router should be stopped")
   (assoc this
          :http-router nil)))

(def http-route-map
  "This defines the mapping between URL prefixes and the
namespaces where the appropriate route handlers live.
Copy/pasted directly from fnhouse."
  {(s/named s/Str "path prefix")
   (s/named s/Symbol "namespace")})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn return-index :- s/Str
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

(s/defn ^:always-validate index-middleware :- fr-ring/HttpRequestHandler
  [handler :- fr-ring/HttpRequestHandler]
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

(s/defn ^:always-validate wrap-sente-middleware :- fr-ring/HttpRequestHandler
  [handler :- fr-ring/HttpRequestHandler
   chsk :- ws/channel-socket]
  (fn [req]
    (let [path (:uri req)]
      (if (= path "/chsk")
        (let [method (:request-method req)]
          (log/debug "Kicking off web socket interaction!\nRequest at this layer:\n"
                     (util/pretty req))
          (condp = method
            :get (let [handler (:ring-ajax-get-or-ws-handshake chsk)
                       response (handler req)]
                   (log/debug "sente's RING ws handshake response:\n"
                              (util/pretty response)
                              "\nfrom Handler:" handler)
                   response)
            :post ((:ring-ajax-post chsk) req)
            (raise {:not-implemented 404})))
        (do
          (log/debug "Sente middleware: just passing along request to" path)
          (handler req))))))

(s/defn debug-middleware :- fr-ring/HttpRequestHandler
  "Log the request as it comes in.

TODO: Should probably save it so we can examine later"
  [handler :- fr-ring/HttpRequestHandler]
  (s/fn :- fr-ring/RingResponse
    [req :- fr-ring/RingRequest]
    (comment (log/debug "Incoming Request:\n" (util/pretty req)))
    (handler req)))

(defn wrap-standard-middleware
  "Build the call stack of everything the standard handlers go through.

  This seems to inevitably turn into a big, hairy, nasty, tangled-up mess.

  Take the time to learn and appreciate everything that's going on here."
  [handler]
  (let [ring-defaults-config
        ;; Note that this will [probably] be sitting behind nginx in most
        ;; scenarios, so should really be using secure-site-defaults with
        ;; :proxy true (c.f. the ring-defaults README).
        ;; If only because of the current pain involved with setting up
        ;; an SSL signing chain, it really isn't feasible to just make that
        ;; the default.
        ;; (i.e. if this is a general consumer app that gets installed
        ;; everywhere, there won't be a real cert. For cases where the
        ;; server is run by someone who cares, there will).
        ;; TODO: Need a way to toggle that option
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                  ;; This is the value recommended in the sente example project.
                  ;; Note that this absolutely does work. I just spent the
                  ;; evening experimenting with that.
                  ;; The problem is absolutely my code.
                  ;; Q: where's the difference?
                  {:read-token (fn [req] (-> req :params :csrf-token))}
                  ;; This is what worked for me originally.
                  ;; According to the ring.middleware.defaults docs, this is what
                  ;; I should be using
                  ;; Q: Why does neither seem to work?
                  ;; TODO: Create a PR to reflect this?
                  #_true)]
    (-> handler
        debug-middleware  ; TODO: Only in debug mode
        ;; Q: Is there any point to this at all?
        ;;wrap-stacktrace   ; TODO: Also just in debug mode
        (ring.middleware.defaults/wrap-defaults ring-defaults-config)
        (wrap-resource "public")
        (wrap-content-type)
        (wrap-not-modified))))

(s/defn attach-docs :- (s/=> fn-schemas/API handlers/Resources)
  "This is really where the fnhouse magic happens
It's almost exactly the same as handlers/nss->handlers-fn
The only difference seems to be attaching extra documentation pieces to the chain

TODO: What does that gain me?"
  [component :- HttpRoutes
   route-map :- http-route-map]
  (let [proto-handlers (handlers/nss->proto-handlers route-map)
        all-docs (docs/all-docs (map :info proto-handlers))]
    (-> component
        (assoc :api-docs all-docs)
        ((handlers/curry-resources proto-handlers)))))

(s/defn fnhouse-handling
  "Convert the fnhouse routes defined in route-map to actual route handlers,
making the component available as needed"
  [component :- HttpRoutes
   route-map :- http-route-map]
  (let [routes-with-documentation (attach-docs component route-map)
        ;; TODO: if there's middleware to do coercion, add it here
        ]
    (routes/root-handler routes-with-documentation)))

(s/defn wrapped-root-handler
  "Returns a handler (with middleware) for 'normal' http requests"
  [component :- HttpRoutes]
  (-> (fnhouse-handling component {"v1" 'com.frereth.web.routes.v1})
      index-middleware
      (wrap-sente-middleware (-> component :web-sock-handler :ch-sock))
      wrap-standard-middleware))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- HttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
