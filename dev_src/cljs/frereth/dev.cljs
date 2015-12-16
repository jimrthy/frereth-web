(ns frereth.dev
    (:require
     [frereth.core]
     [frereth.globals :as global]
     [frereth.system :as system]
     [figwheel.client :as fw]))

(enable-console-print!)

(defn init
  "Constructs the current development system."
  []
  (set! *print-length* 50)
  ;; Q: Will there ever be any valid reason to use a different
  ;; system descriptor for dev than prod?
  ;; Well, besides for the things that will obviously change, like
  ;; URLs and passwords
  (alter-var-root #'global/system
                  (constantly (system/ctor nil nil))))

(defn start
  "Starts the current development system"
  []
  (try
    (alter-var-root #'system component/start)
    (catch RuntimeException ex
      (try
        (log/error ex "Failed to start system")
        (catch RuntimeException ex1
          ;; It seems like I'm getting here when we can't pretty-print
          ;; the stack trace from the original ex
          (log/error ex1 "Failed to log a system start error\n"
                     (pr-str ex))))
      (throw ex))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running.
Can't just call this go: that conflicts with a macro from core.async."
  []
  (println "Initializing system")
  (init)
  (println "Restarting system")
  (start))

(defn reset []
  (println "Stopping")
  (stop)
  (println "Refreshing namespaces")
  ;; This pulls up a window (in the current thread) which leaves
  ;; emacs unusable. Get it at least not dying instantly from lein run,
  ;; then make this play nicely with the main window in a background
  ;; thread.
  ;; Which doesn't really work at all on a Mac: more impetus than
  ;; ever to get a REPL working there internally.
  ;; But I don't need it yet.
  (comment (raise :currently-broken))
  (try
    (refresh :after 'dev/go)
    (catch clojure.lang.ExceptionInfo ex
      (pprint ex)
      (println "Refresh failed"))))

(fw/start {
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :on-jsload (fn []
               (reset)
               (println "Loaded"))})
