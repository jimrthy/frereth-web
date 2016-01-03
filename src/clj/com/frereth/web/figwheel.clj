(ns com.frereth.web.figwheel
  "Thin Componentwrapper around figwheel"
  (:require [com.stuartsierra.component :as cpt]
            [figwheel-sidecar.repl-api :as repl-api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Figwheel []
  component/Lifecycle
  (start [config]
    (repl-api/start-figwheel! config)
    config)
  (stop [config]
    ;; May not actually want to stop figwheel, in general
    ;; TODO: Strongly consider commenting this out
    (repl-api/stop-figwheel! config)
    config))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [_]
  (->Figwheel))
