(ns com.frereth.web.routes.websock
  "For the event handlers around a sente-based web socket

Actually, it probably doesn't have to center around
sente at all."
  (:require [clojure.core.async :as async]
            [clojure.core.match :refer [match]]
            [com.frereth.client.connection-manager :as con-man]
            [com.frereth.common.schema :as fr-skm]
            [com.frereth.common.util :as util]
            [com.frereth.web.routes.ring :as fr-ring]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (sente-web-server-adapter)]
            [taoensso.timbre :as log :refer (debugf)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def sente-event-type
  "Note that this must be namespaced, according to the docs"
  s/Keyword)

(def sente-event
  [sente-event-type s/Any])

(def WebSocketDescription
  "Q: What's this for?"
  {})

(def channel-socket
  {:ring-ajax-post fr-ring/HttpRequestHandler
   :ring-ajax-get-or-ws-handshake fr-ring/HttpRequestHandler
   :receive-chan fr-skm/async-channel
   :send! (s/=> s/Any)
   :connected-uids fr-skm/atom-type})

(declare make-channel-socket reset-web-socket-handler!)
(s/defrecord WebSockHandler [ch-sock :- (s/maybe channel-socket)
                             frereth-server
                             ws-controller :- (s/maybe fr-skm/async-channel)
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
         almost-started (assoc this
                               :ch-sock ch-sock
                               :ws-controller ws-controller)
         web-sock-stopper (reset-web-socket-handler! almost-started)]
     (assoc almost-started
            :ws-stopper web-sock-stopper)))
  (stop
   [this]
   (log/debug "Closing the ws-controller channel")
   (when ws-controller (async/close! ws-controller))
   (log/debug "Calling ws-stopper...which should be redundant")
   (when ws-stopper
     (ws-stopper))
   (assoc this
          :ch-sock nil
          :ws-controller nil
          :ws-stopper nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn ^:always-validate reply
  [this :- WebSockHandler   ; Q: Will there ever be any reason for this?
   {:keys [?reply-fn send-fn id]}
   event-key :- s/Keyword
   event-data :- s/Any]
  (let [response [event-key event-data]]
    (if ?reply-fn
      (?reply-fn response)
      ;; The id is really the event key. It's useless here.
      (send-fn id response))))

(s/defn not-found
  [this :- WebSockHandler
   ev-msg]
  (reply this ev-msg :http/not-found {:status 404 :body "\"Not Found\""}))

(s/defn forward
  [this :- WebSockHandler
   {:keys [send-fn] :as msg}]
  (raise :not-implemented))

(s/defn initiate-auth!
  [this :- WebSockHandler
   ev-msg]
  (async/thread
    (let [cpt (-> this :frereth-server :connection-manager)
          responder (con-man/initiate-handshake cpt 5 2000)
          response-chan (:respond responder)
          [v c] (async/alts!! [response-chan (async/timeout 1000)])]
      (if v
        (do
          (log/debug "Initiating handshake w/ Server returned:\n" (util/pretty v))
          (reply this ev-msg :http/ok {:status 200 :body v}))
        (let [msg (if (= c response-chan)
                    "Handshaker closed response channel. This is bad."
                    "Timed out waiting for response. This isn't great")]
          (reply this ev-msg :http/internal-error {:status 500 :body (pr-str msg)}))))))

(s/defn event-handler
  [this :- WebSockHandler
   {:keys [id ?data event] :as ev-msg}]
  (when-not (= event [:chsk/ws-ping])
    ;; The ws-ping happens every 20 seconds.
    ;; And every 2...I may be setting up multiple event loops on the client
    ;; during a refresh
    ;; TODO: Verify that, one way or another.
    (log/debug "Event: " event
               " Data: " ?data
               " ID: " id))
  (match [event ?data]
         [:frereth/blank-slate _] (initiate-auth! this ev-msg)
         [:frereth/pong _] (forward this ev-msg)
         :else (not-found this ev-msg)))

(s/defn handle-ws-event-loop-msg :- s/Any
  "Refactored from them middle of the go block
created by reset-web-socket-handler! so I can
update on the fly.

Besides, it's much more readable this way"
  [{:keys [ch rcvr web-sock-handler] :as bundle}
   msg :- s/Any]
  (when-not web-sock-handler
    (log/error "Missing web socket handler in:\n" (util/pretty bundle))
    (raise :what-do-I-have-wrong?))
  (if (= ch rcvr)
    (do
      (comment (log/debug "Incoming message from a browser"))
       (event-handler web-sock-handler msg))
    (let [responder (:response msg)]
      (log/debug "Status check")
      (when-not responder
        (log/error "Bad status request message. Missing :response in:\n" msg)
        (assert false))
      (let [ws-controller (:ws-controller web-sock-handler)]
        (assert (= ch ws-controller)))
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
  [{:keys [ch-sock ws-controller ws-stopper] :as web-sock-handler} :- WebSockHandler]
  (let [stop-fn ws-stopper]
    (io!
     (when stop-fn
       (stop-fn))
     (let [stopper (async/chan)
           rcvr (:receive-chan ch-sock)
           event-loop
           (async/go
             (loop []
               (comment (log/debug "Top of websocket event loop\n" (util/pretty {:stopper stopper
                                                                                 :receiver rcvr
                                                                                 :ws-controller ws-controller
                                                                                 :web-sock-handler ((complement nil?) web-sock-handler)})))
               (let [t-o (async/timeout (* 1000 60 5))
                     [v ch] (async/alts! [t-o stopper rcvr ws-controller])]
                 (if v
                   (do
                     (try
                       (handle-ws-event-loop-msg {:ch ch
                                                  :rcvr rcvr
                                                  :web-sock-handler web-sock-handler}
                                                 v)
                       (catch Exception ex
                         (log/error ex "Failed to handle message:\n" v)))
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
       exit-fn))))

;; For testing a status request
;; Wrote it to debug what was going on w/ my
;; disappearing event loop, then spotted that
;; problem before I had a chance to test this.
;; I really think this is/will be useful, but
;; this version is pretty pointless.
(comment
  (let [responder (async/chan)
        controller (-> dev/system :http-router :w-sock-handler :ws-controller)
        [v c] (async/alts!! [(async/timeout 350) [controller {:response responder}]])]
    (if v
      (let [[v c] (async/alts!! [(async/timeout 350) responder])]
        (if v
          (log/info v)
          (log/warn "Timed out waiting for a response")))
      (log/error "Timed out trying to submit status request"))
    (async/close! responder)))

(s/defn extract-user-id-from-request :- s/Str
  "This is more complicated than it appears at first"
  [req :- fr-ring/RingRequest]
  ;; "Official" documented method for getting authenticated per-tab
  ;; session ID.
  ;; From sente's FAQ:
  ;; I.e. sessions (+ some kind of login procedure) are used to
  ;; determine a :base-user-id. That base-user-id is then joined with
  ;; each unique client-id. Each ta therefore retains its own user-id,
  ;; but each user-id is dependent on a secure login procedure.
  (comment (str (get-in req [:session :base-user-id]) "/" (:client-id req)))
  (log/error "Trying to extract the user ID from a request:\n"
             (util/pretty req))
  ;; This is pretty much the simplest possible implementation.
  ;; When a tab connects, use the client ID it generated randomly
  (:client-id req))

(s/defn make-channel-socket :- channel-socket
  []
  (let [{:keys [ ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
        (sente/make-channel-socket! sente-web-server-adapter {:packer :edn
                                                              :user-id-fn extract-user-id-from-request})]

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
  [router :- WebSockHandler
   msg :- s/Any]
  (let [ch-sock (:ch-sock router)
        uids (-> ch-sock :connected-uids deref :any)
        send! (:send! ch-sock)]
    (doseq [uid uids]
      (send! uid msg))))
(comment (broadcast!
          (:http-router dev/system)
          [:frereth/ping nil]))

(s/defn ctor :- WebSockHandler
  [src]
  (map->WebSockHandler src))
