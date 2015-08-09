(ns com.frereth.web.routes.core
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [com.frereth.common.schema :as fr-skm]
            [com.frereth.common.util :as util]
            [com.frereth.client.communicator :as comms]
            [com.frereth.web.routes.ring :as fr-ring]
            [com.frereth.web.routes.v1]  ; Just so it gets compiled, so fnhouse can find it
            [com.frereth.web.routes.websock :as websock]
            [com.stuartsierra.component :as component]
            [fnhouse.docs :as docs]
            [fnhouse.handlers :as handlers]
            [fnhouse.routes :as routes]
            [fnhouse.schemas :as fn-schemas]
            [ribol.core :refer [raise]]
            ;; I have dependencies on several other ring wrappers.
            ;; Esp. anti-forgery, defaults, and headers.
            ;; TODO: Make use of those
            [ring.middleware.content-type :refer (wrap-content-type)]
            [ring.middleware.format :refer (wrap-restful-format)]
            [ring.middleware.keyword-params :refer (wrap-keyword-params)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.middleware.stacktrace :refer (wrap-stacktrace)]
            [ring.util.response :as response]
            [schema.core :as s]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (sente-web-server-adapter)]
            [taoensso.timbre :as log])
  (:import [com.frereth.client.communicator ServerSocket]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def StandardDescription {})
(def StandardCtorDescription {:http-router StandardDescription})
(def WebSocketDescription {})

(def channel-socket
  {:ring-ajax-post fr-ring/HttpRequestHandler
   :ring-ajax-get-or-ws-handshake fr-ring/HttpRequestHandler
   :receive-chan fr-skm/async-channel
   :send! (s/=> s/Any)
   :connected-uids fr-skm/atom-type})

(declare make-channel-socket reset-web-socket-handler! wrapped-root-handler)
;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord HttpRoutes [ch-sock :- channel-socket
                         frereth-client :- ServerSocket
                         http-router :- fr-ring/HttpRequestHandler
                         ws-router :- fr-skm/async-channel]
  component/Lifecycle
  (start
   [this]
   (let [ch-sock (or ch-sock
                     ;; This brings up an important question:
                     ;; how (if at all) does the web socket handler
                     ;; interact with the "normal" HTTP handlers?
                     ;; It seems like there is a lot of ripe fruit for
                     ;; the plucking here.
                     ;; TODO: Pick an initial approach.
                     ;; Probably shouldn't create either in here.
                     ;; This is really just for the sake of wiring
                     ;; together all the communications pieces with
                     ;; whatever dependencies may be involved.
                     (make-channel-socket))
         web-sock-router (reset-web-socket-handler! ws-router ch-sock)
         has-web-sock (assoc this
                             :ch-sock ch-sock
                             :ws-router web-sock-router)]
     (assoc has-web-sock
            :http-router (wrapped-root-handler has-web-sock))))
  (stop
   [this]
   (when ws-router
     (async/close! ws-router))
   (assoc this
          :ch-sock nil
          :http-router nil
          :ws-router nil)))

(def UnstartedHttpRoutes
  "Q: Where is this used?"
  (assoc StandardDescription
         :ch-sock (s/maybe channel-socket)
         :http-router s/Any
         :frereth-client s/Any
         :ws-router (s/maybe channel-socket)))

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
   chsk :- channel-socket]
  (fn [req]
    ;; Verified: We are getting here
    (comment (log/debug "Sente middleware wrapper:\nRequest:" req))
    (let [path (:uri req)]
      (if (= path "/chsk")
        (let [method (:request-method req)]
          (log/debug "Kicking off web socket interaction!")
          (condp = method
            :get ((:ring-ajax-get-or-ws-handshake chsk) req)
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
      ;; TODO: Should probably just be using ring.middleware.defualts
      ;; From the sente example project:
      #_(let [ring-defaults-config
            (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                      {:read-token (fn [req]
                                     (-> req :params :csrf-token))})]
        (ring.middleware.defaults/wrap-defaults route-globals ring-defaults-config))
      #_(wrap-restful-format :formats [:edn :json-kw :yaml-kw :transit-json :transit-msgpack])
      ;; These next two are absolutely required by sente
      wrap-keyword-params
      wrap-params  ; Q: How does this interact w/ wrap-restful-format?
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

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
      (wrap-sente-middleware (:ch-sock component))
      wrap-standard-middleware))

(s/defn reset-web-socket-handler! :- (s/=> s/Any)
  "Replaces the existing web-socket-handler (aka router)
(if any) with a new one, built around ch-sock.
Returns a function for closing
the event loop (which should be returned in the next
call as the next stop-fn)"
  [stop-fn :- (s/=> s/Any)
   ch-sock :- fr-skm/async-channel]
  (when stop-fn
    (stop-fn))
  (sente/start-chsk-router! ch-sock websock/event-handler))

(s/defn make-channel-socket  :- channel-socket
  []
  (let [{:keys [ ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
        (sente/make-channel-socket! sente-web-server-adapter {})]

    {:ring-ajax-post ajax-post-fn
     :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
     ;; ChannelSocket's receive channel
     ;; This is half of the server-side magic:
     ;; Messages from clients will show up here
     ;; The docs are confusing about this. It looks like
     ;; the incoming messages follow the form:
     ;; {:event _ :send-fn _ :?reply-fn _ :ring-req _}
     ;; Whereas the client is responsible for sending
     ;; {:event _ :send-fn _ & args}

     :receive-chan ch-recv
     ;; ChannelSocket's send API fn
     ;; This is the other half of the server-side magic.
     ;; Its parameters are [user-id event]
     :send! send-fn
     :connected-uids  connected-uids}))    ; Watchable, read-only atom

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn broadcast!
  [router :- HttpRoutes
   msg :- s/Any]
  (let [ch-sock (:ch-sock router)
        uids (-> ch-sock :connected-uids deref :any)
        send! (:send! ch-sock)]
    (doseq [uid uids]
      (send! uid msg))))
(comment (broadcast!
          (:http-router dev/system)
          [:frereth/ping nil]))

(s/defn ^:always-validate ctor :- UnstartedHttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
