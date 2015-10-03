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
            [ribol.core :refer (manage on raise)]
            [schema.core :as s]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (sente-web-server-adapter)]
            [taoensso.timbre :as log :refer (debugf)])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def sente-event-type
  "Note that this must be namespaced, according to the docs"
  s/Keyword)

(def sente-event
  [sente-event-type s/Any])

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

(s/defn reply
  [this :- WebSockHandler   ; Q: Will there ever be any reason for this?
   {:keys [data? id ?reply-fn send-fn uid]}
   event-key :- s/Keyword
   event-data :- s/Any]
  (let [msg (str "replying to: " id
                 "\n\tthis: "(keys this) ", a " (class this)
                 "\n\tevent-key: " event-key
                 "\n\tevent-data: " event-data)]
    (log/debug msg))
  (try
    ;; One really obnoxious bit about the weirdness I'm running across here:
    ;; I'm not even using this
    ;; Q: What weirdness?
    (let [actual (s/validate WebSockHandler this)]
      (let [response [event-key event-data]]
        (if ?reply-fn
          (?reply-fn response)
          (send-fn uid response))))
    (catch ExceptionInfo ex
      (let [msg (str "WebSockHandler fails to validate.\n"
                     (util/pretty (.getData ex)))]
        (log/error ex msg)))))

(s/defn ^:always-validate post
  [send-fn
   id
   event-key :- s/Keyword
   event-data :- s/Any]
  ;; Q: Should I prefer client-id or uid?
  (send-fn id [event-key event-data]))

(s/defn not-found
  [this :- WebSockHandler
   ev-msg]
  (log/error "No handler for:\n" (util/pretty ev-msg))
  (reply this ev-msg :http/not-found {:status 404 :body "\"Not Found\""}))

(s/defn forward
  [this :- WebSockHandler
   {:keys [send-fn] :as msg}]
  (raise :not-implemented))

(s/defn initiate-auth!
  [this :- WebSockHandler
   ev-msg]
  (log/debug "Initial connection to AUTH on server")
  (async/thread
    (let [cpt (-> this :frereth-server :connection-manager)]
      (if-let [responder (con-man/initiate-handshake cpt 5 2000)]
        (let [response-chan (:respond responder)
              [v c] (async/alts!! [response-chan (async/timeout 1000)])]
          (if v
            (do
              (log/debug "Initiating handshake w/ Server returned:\n" (util/pretty v))
              (reply this ev-msg :frereth/response {:status 200 :body "Handshake Completed"})
              (let [{:keys [uid client-id]} ev-msg
                    id (or client-id uid)]
                (post (:send-fn ev-msg) id :frereth/start-world v)))
            (let [msg (if (= c response-chan)
                        "Handshaker closed response channel. This is bad."
                        "Timed out waiting for response. This isn't great")]
              (reply this ev-msg :http/internal-error {:status 500 :body (pr-str msg)}))))
        (reply this ev-msg :http/bad-gateway {:status 502 :body "Handshake initiation failed"})))))

(s/defn ping
  [this :- WebSockHandler
   ev-msg]
  ;; Might as well take advantage of what's available
  (log/debug "Pinged!\nTODO: Forward this along to the server"))

(s/defn request-ns-load!
  [this :- WebSockHandler
   {:keys [module-name macro? path world] :as data}]
  ;; Q: Does it make sense to spawn the thread here? Or in the caller?
  ;; A: It depends on whether these handlers should always run in their
  ;; own thread, of course.
  ;; For that matter, it might make the most sense to just pass a
  ;; parameter to rpc to decide whether to spawn a new thread or not
  (async/thread
    (let [mgr (-> this :frereth-server :connection-manager)
          response
              (manage
           (con-man/rpc-sync world
                        :frereth/load-ns
                        (select-keys [module-name macro? path]))
           ;; For now, just insert handlers directly into the code
           ;; to be compiled
           ;; TODO: Come up w/ a better strategy
           ;; If nothing else, this sort of thing seems like it would be
           ;; more appropriate in the client.
           ;; Except that, as far as the client is concerned, there's
           ;; no real hint about what we're doing.
           ;; So maybe this should be a wrapper in there around rpc
           ;; Get it working first, then worry about getting it to work
           ;; correctly.
           (on :timeout []
               {:script '(raise :timeout)}))]
      (reply this
             data
             :frereth/loaded-ns
             response))))

(s/defn initialize-connection!
  [this :- WebSockHandler
   {:keys [id ?data event]}]
  ;; Q: Is there anything useful I can do here?
  ;; A: Well, could start priming client connection to make sure
  ;; its initial Auth piece is ready to go.
  ;; Since that's coming next.
  (log/info "Browser connected"))

(s/defn event-handler
  [this :- WebSockHandler
   {:keys [id ?data event] :as ev-msg}]
  (when-not (= event [:chsk/ws-ping])
    ;; The ws-ping happens every 20 seconds.
    ;; And every 2...I may be setting up multiple event loops on the client
    ;; during a refresh
    ;; TODO: Verify that, one way or another.
    (log/debug "Event: " event
               "\nData: " ?data
               "\nID: " id
               "\nout of keys:\n" (keys ev-msg)))
  (match [id ?data]
         ;; As it stands, this looks like a simple cond on the id
         ;; would make more sense.
         ;; Or possibly a multimethod that dispatches on id
         ;; For that matter, I could just use a map of message id's to handler
         ;; fn.
         ;; Q: Will I ever want different functionality based on the data?
         [:chsk/uidport-open _] (initialize-connection! this ev-msg)
         ;; TODO: This seems like a pretty important detail
         [:chsk/uidport-close _] (log/error "Remote port closed: need to clean up its resources")
         [:chsk/ws-ping _] (ping this ev-msg)
         [:frereth/blank-slate _] (initiate-auth! this ev-msg)
         [:frereth/load-ns _] (request-ns-load! this ev-msg)
         [:frereth/pong _] (forward this ev-msg)
         [:frereth/response _] (log/info "Response from renderer:\n"
                                         (util/pretty ?data))
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
               (log/debug "Top of websocket event loop"  #_(util/pretty {:stopper stopper
                                                                         :receiver rcvr
                                                                         :ws-controller ws-controller
                                                                         :web-sock-handler ((complement nil?) web-sock-handler)}))
               ;; TODO: I'm pretty sure I have 5 minutes defined somewhere useful
               (let [t-o (async/timeout (* 1000 60 5))
                     [v ch] (async/alts! [t-o stopper rcvr ws-controller])]
                 (if v
                   (do
                     (let [event (-> v :event first)]
                       (when-not (= event :chsk/ws-ping)
                         ;; Handle it, but don't log about it: this happens far too often
                         ;; to spam the logs with the basic fact
                         (log/debug "Message to handle:\n" (util/pretty v))))
                     (try
                       (handle-ws-event-loop-msg {:ch ch
                                                  :rcvr rcvr
                                                  :web-sock-handler web-sock-handler}
                                                 v)
                       (catch Exception ex
                         (log/error ex "Failed to handle message:\n" (util/pretty v))))
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
;; Wrote this to debug what was going on w/ my
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
;; Just for testing that in REPL
(comment (broadcast!
          (:http-router dev/system)
          [:frereth/ping nil]))

(s/defn ctor :- WebSockHandler
  [src]
  (map->WebSockHandler src))
