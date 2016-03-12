(ns com.frereth.web.figwheel
  "Thin Componentwrapper around figwheel

Seems to be obsolete and wrong.

The sidecar README seems to recommend tighter Components integration:
Load the figwheel.edn (or project.clj if that doesn't exist) and return
an init'd Component that's ready to start:

(let [fig-cfg (figwheel-sidecar.system/fetch-config)
      fig-sys (figwheel-sidecar.system/fig-system fig-cfg)])"
  (:require [com.stuartsierra.component :as cpt]
            [figwheel-sidecar.repl-api :as repl-api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Figwheel [fig]
  cpt/Lifecycle
  (start [config]
    (repl-api/start-figwheel! config)
    config)
  (stop [config]
    ;; TODO: This approach is totally wrong
    (repl-api/stop-figwheel!)
    config))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [_]
  (map->Figwheel {}))
