(ns ^:figwheel-always frereth.core
    "This part should trigger the system start, and not much more

Right now, that isn't the case at all."
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                     [schema.macros :as sm]
                     [schema.core :as s])
    (:require [cljs.core.async :as async]
              [com.stuartsierra.component :as component]
              [frereth.dispatcher :as dispatcher]
              [frereth.globals :as global]
              [frereth.system :as system]
              [taoensso.sente :as sente :refer (cb-success?)]
              [taoensso.timbre :as log]))
(enable-console-print!)

(def five-seconds-in-ms (* 1000 5))
(def five-minutes-in-ms (* 60 five-seconds-in-ms))

(defonce event-loop-counter
  ;; "Give this a label so I can track which it is"
  (atom 0))
(defn start-event-handler!  ; returns a map of the go block wrapping the event loop and an atom to tell it to stop
  ([{:keys [socken recv-chan send! state]
     :as socket-description}
    event-loop-number]
   (let [done (atom false)  ; this would be a promise, if cljs offered such a construct
         event-loop
         (go
           (loop []
             (log/debug "Top of websocket event handling loop #:" event-loop-number)
             (let [[incoming ch]
                   (async/alts! [(async/timeout five-minutes-in-ms) recv-chan])]
               (if-let [event-keys (keys incoming)]
                 (log/debug "event-loop" event-loop-number "received keys:\n" (pr-str event-keys))
                 (log/debug "event-loop" event-loop-number "received:\n" (pr-str incoming)))
               (if (= recv-chan ch)
                 (try
                   ;; Or maybe this is the start of a message batch?
                   ;; This is really a pair of async-receive-channel
                   ;; and send function
                   (if incoming
                     (do
                       (log/debug "Dispatching: " (pr-str (dissoc incoming :send-fn)))
                       (dispatcher/handle! incoming))
                     (do
                       (log/info "Event loop" event-loop-number "Channel closed. We're done here")
                       (reset! done true)))
                   (catch js/Object ex
                     (log/error ex (str "Error escaped event handler #" event-loop-number))))
                 (do
                   (assert (nil? incoming))
                   (log/debug "Background Async WS Loop: Heartbeat on event loop #" event-loop-number))))
             (when-not @done
               (recur)))
           ;; TODO: Really need to associate some sort of name/ID with
           ;; each socket. It's confusing when figwheel reloads and a
           ;; new one gets established.
           ;; Honestly, that shouldn't happen.
           ;; But I have other things that seem higher priority to worry about.
           (log/warn "Event Handler #" event-loop-number "for [ultimate ch-sock] exited"))]
     {:event-loop event-loop
      :done done}))
  ([socket-description]
   (swap! event-loop-counter inc)
   (start-event-handler! socket-description @event-loop-counter)))

(defn client-sock
  []
  (let [{:keys [chsk ch-recv send-fn state]}
        ;; Note path to request-handler on server
        (sente/make-channel-socket! "/sente/chsk"
                                    {:type :auto})]
    (log/debug "Client connection built. Creating event loop handling thread")
    (let [sock {:socket chsk
                :recv-chan ch-recv
                :send! send-fn
                :state state}
          ;; It's tempting to switch to sente's start-chsk-router!
          ;; But this half is working, so it doesn't seem worthwhile
          ;; OTOH, deleting code in favor of something standard that
          ;; other are using/testing usually
          ;; is a great improvement
          pending-server-handshake
          (go
            (loop [n 5]   ; FIXME: No magic numbers
              (when (< 0 n)
                (let [[handshake-response ch] (async/alts! [(async/timeout five-seconds-in-ms) ch-recv])]
                  (if (= ch ch-recv)
                    (if handshake-response
                      (if-let [event (:event handshake-response)]
                        (let [[kind body] event]
                          (log/info "Initial socket response:\n\t" kind
                                    "\n\t" body)
                          ;; This is really the handshake message
                          (assert (= :chsk/state kind))
                          (assert (:first-open? body))
                          ;; The body has the :uid.
                          ;; Q: Is there any point to saving it?
                          (let [event-handling-go-block (start-event-handler! sock)]
                            event-handling-go-block)))
                      (log/error "Receive-channel closed. What happened?"))
                    (do
                      (log/warn "Timed out waiting for server response: "
                                (dec n) " attempts left")
                      (recur (dec n))))))))]
      (assoc sock :pending-server-handshake pending-server-handshake))))

(defn channel-swapper
  "Replace an existing sente connection  [if any] to the web server with a fresh one"
  [current latest]
  (when-let [existing-ws-channel (:channel-socket current)]
    (log/debug "Replacing existing channel-socket")
    (if-let [receiver (:recv-chan existing-ws-channel)]
      (async/close! receiver)
      (log/info "Swapping non-existent receiver among " (keys existing-ws-channel))))
  (assoc current :channel-socket latest))

(defn send-event!
  "event-type must be a namespaced keyword
  e.g. [:chsk/handshake [<?uid> <?csrf-token> <?handshake-data>]]"
  ([event-type]
   (send-event! event-type {}))
  ([event-type args]
   (let [sender (-> global/app-state deref :channel-socket :send!)]
     (sender [event-type args]))))

(defn -main
  []
  (log/debug "Starting initial World")

  ;; This really shouldn't be a global. It should be a local hidden
  ;; inside here.
  ;; That makes it very problematic to access via the REPL for
  ;; examining and debugging.
  ;; And, really, the problem with globals is mutating them.
  ;; Since this really does involve mutable state, I'm squeamish
  ;; about handling it this way.
  ;; For now, run with it.
  ;; TODO: Keep an eye out for better alternatives
  (reset! global/app-state system/ctor)

  ;;; TODO: Move this to the initial world/localhost's ctor
  ;;; Except that it needs to be more generalized. Could also
  ;;; be contacting other servers directly via their clients to
  ;;; avoid needless network hops
  (log/debug "Initializing contact w/ outside world")
  (go
    (let [{:keys [pending-server-handshake]
           :as new-client-socket} (client-sock)
           msg (str "client-sock has returned a go-loop that's\ninitializing the connection:\n" (keys new-client-socket))]
      (log/debug msg)
      (swap! global/app-state
             channel-swapper new-client-socket)
      ;; This should pause, waiting for the result of
      ;; channel-creation-loop, then swap the global/app-state's
      ;; :channel-socket with whatever it returns
      (let [channel-creation-result (<! pending-server-handshake)]
        (log/debug "Server fresh connection request returned:\n"
                   (pr-str channel-creation-result))))
    (log/info "-main: Connected to outside world"))

  (swap! global/app-state component/start)
  ;; Q: Should I ever plan on stopping it?
  )
;; Because I'm not sure how to trigger this on a page reload
;; (there's a built-in figwheel method precisely for this)
;; Really just for scaffolding: I won't want to do this after I'm
;; confident about setup/teardown
(-main)
