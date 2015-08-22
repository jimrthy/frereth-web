(ns ^:figwheel-always frereth.core
    "This part should trigger the system start, and not much more

Right now, that isn't the case at all."
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                     [schema.macros :as macros]
                     [schema.core])
    (:require [cljs.core.async :as async]
              [frereth.dispatcher :as dispatcher]
              [frereth.globals :as global]
              [frereth.repl :as repl]
              [frereth.three :as three]
              [taoensso.sente :as sente :refer (cb-success?)]
              [taoensso.timbre :as log]))
(enable-console-print!)

(println "Top of core")

(defn start-event-handler!
  [socket-description]
  (let [done (atom false)
        recv (:recv-chan socket-description)]
    (go
      (loop []
        (log/debug "Top of websocket event handling loop with " (pr-str recv)
                   " a [cryptic function]" #_(type recv)
                   "\nout of: " (keys socket-description))
        (let [event-pair
              (try
                (async/alts! [(async/timeout (* 1000 60 5)) recv])
                (catch js/Error ex
                  ;; I'm getting an Error that looks like it's happening here
                  ;; that "[object Object] is not ISeqable"
                  ;; This isn't the case, since we just yielded control
                  ;; to wait on the incoming event
                  (log/error ex "Yep, trying to call alts! is failing")))
              _ (log/debug "alts! returned: " event-pair)
              [incoming ch] event-pair]
          (log/debug "Some sort of message received")
          (if (= recv ch)
            (try
              ;; Or maybe this is the start of a message batch?
              ;; This is really a pair of async-receive-channel
              ;; and send function
              (log/debug "Incoming message:\n")
              (dispatcher/handle! incoming)
              (when-not incoming
                (println "Channel closed. We're done here")
                (reset! done true))
              (catch js/Object ex
                (log/error ex "Error escaped event handler")))
            (do
              (log/info "Background Async WS Loop: Heartbeat"))))
        (when-not @done
          (recur)))
      (log/warn "Event Handler for " socket-description " exited"))))

(defn client-sock
  []
  (log/info "Building client connection")
  (let [{:keys [chsk ch-recv send-fn state]}
        ;; Note path to request-handler on server
        (sente/make-channel-socket! "/sente/chsk"
                                    {:type :auto})]
    (log/debug "Client connection built")
    (let [sock {:socket chsk
                :recv-chan ch-recv
                :send! send-fn
                :state state}
          ;; It's tempting to switch to sente's start-chsk-router!
          ;; But this half is working, so it doesn't seem worthwhile
          ;; OTOH, deleting code in favor of something standard that
          ;; other are using/testing usually
          ;; is a great improvement
          ;; And that "is working" part of the comment is very debatable
          pending-server-handshake
          (go
            (loop [n 5]   ; FIXME: No magic numbers
              (when (< 0 n)
                ;; FIXME: No magic numbers here, either
                (let [[handshake-response ch] (async/alts! [(async/timeout 5000) ch-recv])]
                  (if (= ch ch-recv)
                    (do
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
                            (log/debug "start-event-handler! returned:\n"
                                       event-handling-go-block)
                            (let [result (async/<! event-handling-go-block)]
                              (log/debug "start-event-handler!'s go-block returned:\n"
                                         result)
                              result)))))
                    (do
                      (log/warn "Timed out waiting for server response: "
                                (dec n) " attempts left")
                      (recur (dec n))))))))]
      (assoc sock :pending-server-handshake pending-server-handshake))))

(defn channel-swapper
  "Replace an existing sente connection  [if any] to the web server with a fresh one"
  [current latest]
  (when-let [existing-ws-channel (:channel-socket current)]
    (log/debug "Replacing existing channel-socket:\n"
               existing-ws-channel
               "\nwith:\n"
               latest)
    (if-let [receiver (:recv-chan existing-ws-channel)]
      (async/close! receiver)
      (log/info "Swapping non-existent receiver among " (keys existing-ws-channel))))
  (assoc current :channel-socket latest))

(defn send-event
  "event-type must be a namespaced keyword
  e.g. [:chsk/handshake [<?uid> <?csrf-token> <?handshake-data>]]"
  ([event-type]
   (send-event event-type {}))
  ([event-type args]
   (let [sender (-> global/app-state deref :channel-socket :send!)]
     (sender [event-type args]))))

(defn -main
  []
  (repl/start)
  (three/start-graphics js/THREE)
  (go
    (let [{:keys [channel-creation-loop]
           :as new-client-socket} (client-sock)
          msg "channel-creation-loop has returned a go-loop that's
initializing the connection"]
      (log/debug msg)
      ;; This should pause, waiting for the result of
      ;; channel-creation-loop, then swap the global/app-state's
      ;; :channel-socket with whatever it returns
      (let [channel-creation-result (<! channel-creation-loop)]
        (log/debug "Server fresh connection request returned:\n"
                   channel-creation-result)
        (swap! global/app-state
               channel-swapper new-client-socket)))
    (log/info "Connected to outside world")))
;; Because I'm not sure how to trigger this on a page reload
;; (there's a built-in figwheel method precisely for this)
;; Really just for scaffolding: I won't want to do this after I'm
;; confident about setup/teardown
(-main)
