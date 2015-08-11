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
            ;; Q: Am I getting the ring header middleware with this group?
            [ring.middleware.content-type :refer (wrap-content-type)]
            [ring.middleware.defaults]
            [ring.middleware.format :refer (wrap-restful-format)]
            #_[ring.middleware.keyword-params :refer (wrap-keyword-params)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            #_[ring.middleware.params :refer (wrap-params)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.middleware.stacktrace :refer (wrap-stacktrace)]
            [ring.util.response :as response]
            [schema.core :as s]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (sente-web-server-adapter)]
            [taoensso.timbre :as log])
  (:import [com.frereth.client.communicator ServerSocket]
           [com.frereth.web.routes.websock WebSockHandler]))

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
(s/defrecord HttpRoutes [ch-sock :- (s/maybe channel-socket)
                         ws-controller :- (s/maybe fr-skm/async-channel)
                         frereth-client :- (s/maybe ServerSocket)
                         ;; This record winds up in the system as
                         ;; the http-router. It's confusing for it
                         ;; to have another.
                         ;; TODO: change one name or the other
                         http-router :- (s/maybe fr-ring/HttpRequestHandler)
                         web-sock-handler :- (s/maybe WebSockHandler)
                         ws-stopper :- (s/maybe (s/=> s/Any))]
  component/Lifecycle
  (start
   [this]
   ;; This is one scenario where it doesn't make any sense to try to
   ;; recycle the previous version
   (when ws-controller
     (async/close! ws-controller))
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
         ws-controller (async/chan)
         web-sock-stopper (reset-web-socket-handler! ws-stopper ch-sock ws-controller web-sock-handler)
         has-web-sock (assoc this
                             :ch-sock ch-sock
                             :ws-controller ws-controller
                             :ws-stopper web-sock-stopper)]
     (assoc has-web-sock
            :http-router (wrapped-root-handler has-web-sock))))
  (stop
   [this]
   (log/debug "Closing the ws-controller channel")
   (when ws-controller (async/close! ws-controller))
   (log/debug "Calling ws-stopper...which should be redundant")
   (when ws-stopper
     (ws-stopper))
   (log/debug "HTTP Router should be stopped")
   (assoc this
          :ch-sock nil
          :http-router nil
          :ws-controller nil
          :ws-stopper nil)))

(comment
  (def UnstartedHttpRoutes
    "Q: Where is this used?"
    (assoc StandardDescription
           :ch-sock (s/maybe channel-socket)
           :http-router s/Any
           :frereth-client s/Any
           :ws-controller (s/maybe fr-skm/async-channel)
           :ws-stopper (s/maybe channel-socket))))

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
  (let [ring-defaults-config
        ;; Note that this will [probably] be sitting behind nginx in most
        ;; scenarios, so should really be using secure-site-defaults with
        ;; :proxy true (c.f. the ring-defaults README).
        ;; If only because of the current pain involved with setting up
        ;; an SSL signing chain, it really isn't feasible to just make that
        ;; the default.
        ;; TODO: Need a way to turn that option on.
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                  ;; This is the value recommended in the sente example project
                  #_{:read-token (fn [req]
                                   (-> req :params :csrf-token))}
                  ;; This is the value actually required by ring.defaults
                  ;; TODO: Create a PR to reflect this
                  true)]
    (-> handler
        debug-middleware  ; TODO: Only in debug mode
        wrap-stacktrace   ; TODO: Also just in debug mode
        ;; TODO: Should probably just be using ring.middleware.defualts
        ;; From the sente example project:
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
      (wrap-sente-middleware (:ch-sock component))
      wrap-standard-middleware))

(s/defn handle-ws-event-loop-msg :- s/Any
  "Refactored from them middle of the go block
created by reset-web-socket-handler! so I can
update on the fly.

Besides, it's much more readable this way"
  [{:keys [ch rcvr web-sock-handler ws-controller]}
   msg :- s/Any]
  (if (= ch rcvr)
    (do
      (log/debug "Incoming message from a browser")
       (websock/event-handler web-sock-handler msg))
    (let [responder (:response msg)]
      (log/debug "Status check")
      (when-not responder
        (log/error "Bad status request message. Missing :response in:\n" msg)
        (assert false))
      (assert (= ch ws-controller))
      (async/alts! [(async/timeout 100) [responder :Im-alive]]))))

(s/defn reset-web-socket-handler! :- (s/=> s/Any)
  "Replaces the existing web-socket-handler (aka router)
(if any) with a new one, built around ch-sock.
Returns a function for closing
the event loop (which should be returned in the next
call as the next stop-fn)

It's very tempting to try to pretend that this is a pure
function, but its main point is the side-effects around
the event loop"
  [stop-fn :- (s/=> s/Any)
   ch-sock :- fr-skm/async-channel
   ws-controller :- fr-skm/async-channel
   web-sock-handler :- WebSockHandler]
  (io!
   (when stop-fn
     (stop-fn))
   (let [stopper (async/chan)
         rcvr (:receive-chan ch-sock)
         event-loop
         (async/go
           (loop []
             (log/debug "Top of websocket event loop\n" {:stopper stopper
                                                         :receiver rcvr
                                                         :ws-controller ws-controller})
             (let [t-o (async/timeout (* 1000 60 5))
                   [v ch] (async/alts! [t-o stopper rcvr ws-controller])]
               (if v
                 (do
                   (handle-ws-event-loop-msg {:ch ch
                                              :rcvr rcvr
                                              :ws-controller ws-controller
                                              :web-sock-handler web-sock-handler}
                                             v)
                   (recur))
                 (when (= ch t-o)
                   ;; TODO: Should probably post a heartbeat to the
                   ;; "real" server here
                   (log/debug "Websocket event loop heartbeat")
                   (recur)))))
           (log/info "Websocket event loop exited"))
         exit-fn (fn []
                   (when stopper
                     (log/debug "Closing the stopper channel as a signal to exit the event loop")
                     (async/close! stopper)))]
     exit-fn)))

;; For testing a status request
;; Wrote it to debug what was going on w/ my
;; disappearing event loop, then spotted that
;; problem before I had a chance to test this.
;; I really think this is/will be useful, but
;; this version is pretty pointless.
(comment
  (let [responder (async/chan)
        controller (-> dev/system :http-router :ws-controller)
        [v c] (async/alts!! [(async/timeout 350) [controller {:response responder}]])]
    (if v
      (let [[v c] (async/alts!! [(async/timeout 350) responder])]
        (if v
          (log/info v)
          (log/warn "Timed out waiting for a response")))
      (log/error "Timed out trying to submit status request"))
    (async/close! responder)))

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
  "Send message to all attached users"
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

(s/defn ^:always-validate ctor :- HttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
