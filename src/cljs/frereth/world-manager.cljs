(ns frereth.world-manager
  "The thing that controls which Worlds get drawn where.

Might have a lot to do with the event loop, though that really seems
lower level.

Think of an XFree86 Window Manager

This is really only intended as a simple template/example to use as a
basis for building your own

TODO: I really need a better understanding of the way these things actually
work."
  (:require [com.stuartsierra.component :as component]
            [frereth.multiverse :refer (Multiverse)]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord WorldManager [worlds :- Multiverse]
  component/Lifecycle
  (start
      [this]
    this)
  (stop
      [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- WorldManager
  [{:keys [worlds] :as params}]
  (map->WorldManager (select-keys params [:worlds])))
