(ns dev
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.inspector :as i]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            #_[clojurescript-build.auto :as auto]
            #_[com.frereth.common.util :as util]
            [com.frereth.web.system :as system]
            [com.stuartsierra.component :as component]
            [component-dsl.system :as cpt-dsl]  ; Q: Will I really be using this often?
            #_[figwheel-sidecar.auto-builder :as fig-auto]
            #_[figwheel-sidecar.core :as fig]
            #_[figwheel-sidecar.repl-api :as repl-api]
            [taoensso.timbre :as log]))

;; We wrap the system in a system wrapper so that we can define a
;; print-method that will avoid recursion.
(defrecord SystemWrapper [p]
  clojure.lang.IDeref
  (deref [this] (deref p))
  clojure.lang.IFn
  (invoke [this a] (p a)))

(defmethod print-method SystemWrapper [_ writer]
  (.write writer "#system \"<system>\""))

(defmethod print-dup SystemWrapper [_ writer]
  (.write writer "#system \"<system>\""))

(.addMethod clojure.pprint/simple-dispatch SystemWrapper
   (fn [x]
     (print-method x *out*)))

(defn new-system-wrapper []
  (->SystemWrapper (promise)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Components boiler-plate cruft

(def system nil)

(defn init
  "Constructs the current development system."
  []
  (set! *print-length* 50)
  ;; Q: Will there ever be any valid reason to use a different
  ;; system descriptor for dev than prod?
  ;; Well, besides for the things that will obviously change, like
  ;; URLs and passwords
  (alter-var-root #'system
                  (constantly (system/ctor nil nil))))

(defn start
  "Starts the current development system."
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Dev-time conveniences
;;; (though it's important to remember that everything in this
;;; namespace is just a dev-time convenience)

;; Set up a REPL environment.
;; Q: Do I want to add this to start/stop?
(comment (def repl-env nil))

;; TODO: Real CIDER/figwheel integration
(comment
  (defn cljs
    "Switch to the cljs REPL

  There's an emacs plugin for running both @ same time
  TODO: switch to it"
    []
    (repl-api/cljs-repl)))

(comment
  ;; This looks like it works at first, but cider can't connect
  (defn start-figwheel
  "Use figwheel from a Clojure REPL
  It's a work in progress"
  []
  (let [config {:builds [{:id "example"
                          :output-to "resources/public/checkbuild.js"
                          :output-dir "resources/public/out"
                          :optimizations :none}]
                :figwheel-server (fig/start-server {:css-dirs ["resources/public/css"]})}
        builder (fig-auto/autobuild* config)]
    (defn stop-figwheel
      []
      (auto/stop-autobuild! builder)))))
