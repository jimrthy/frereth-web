(ns ^:figwheel-always frereth.core
    "TODO: Do something interesting here"
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                     [schema.macros :as macros]
                     [schema.core])
    (:require [cljs.core.async :as async]
              [frereth.globals :as global]
              [frereth.repl :as repl]
              [frereth.three :as three]
              [taoensso.sente :as sente :refer (cb-success?)]))
(enable-console-print!)

(println "Top of core")

(defn event-handler
  [socket-description]
  (let [done (atom false)
        recv (:recv-chan socket-description)]
    (go
      (loop []
        (println "Top of websocket event handling loop")
        (let [[incoming ch] (async/alts! [(async/timeout (* 1000 60 5)) recv])]
          (if (= recv ch)
            (do
              (println "Incoming message:\n" (:event incoming)
                       "\nTODO: Handle it")
              (when-not incoming
                (println "Channel closed. We're done here")
                (reset! done true)))
            (do
              (println "Background Async WS Loop: Heartbeat"))))
        (when-not @done
          (recur)))
      (println "Event Handler for " socket-description " exited"))))

(defn client-sock
  []
  (println "Building client connection")
  (let [{:keys [chsk ch-recv send-fn state]}
        ;; Note path to request-handler on server
        (sente/make-channel-socket! "/sente/chsk"
                                    {:type :auto})]
    (println "Client connection built")
    (let [result {:socket chsk
                  :recv-chan ch-recv
                  :send! send-fn
                  :state state}]
      ;; It's tempting to switch to sente's start-chsk-router!
      ;; But this half is working, so it doesn't seem worthwhile
      ;; OTOH, deleting code in favor of something standard that
      ;; other are using/testing usually
      ;; is a great improvement
      (go
        (loop [n 5]
          (when (< 0 n)
            (let [[handshake-response ch] (async/alts! [(async/timeout 5000) ch-recv])]
              (if (= ch ch-recv)
                (do
                  (if-let [event (:event handshake-response)]
                    (let [[kind body] event]
                      (println "Initial socket response:\n\t" kind
                               "\n\t" body)
                      (event-handler result))))
                (do
                  (println "Timed out waiting for server response")
                  (recur (dec n))))))))
      result)))

(defn channel-swapper
  "Replace an existing sente connection  [if any] to the web server with a fresh one"
  [current latest]
  (when-let [existing-ws-channel (:channel-socket current)]
    (let [receiver (:recv-chan existing-ws-channel)]
      (async/close! receiver)))
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
  (swap! global/app-state  channel-swapper (client-sock)))
;; Because I'm not sure how to trigger this on a page reload
;; (there's a built-in figwheel method precisely for this)
;; Really just for scaffolding: I won't want to do this after I'm
;; confident about setup/teardown
(-main)
