(ns dev
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.inspector :as i]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.frereth.web.system :as system]
            [integrant.repl :refer (clear go halt init reset reset-all)]
            [taoensso.timbre :as log]))

(def +frereth-component+
  "Just to help me track which REPL is which"
  'web)

(integrant.repl/set-prep! (partial system/ctor {} ""))

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
