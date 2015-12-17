(ns frereth.multiverse
  "Track the connetions/state to all the worlds we know about"
  (:require [com.stuartsierra.component :as component]
            [frereth.schema :as fr-skm]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def world-description
  "Q: What, if anything, makes sense here?
A: Probably the same thing that's probably doing the same
job in globals"
  s/Any)

(def world-map
  {fr-skm/world-id world-description})

(s/defrecord Multiverse [worlds :- world-map]
  component/Lifecycle
  (start
      [this]
    (let [worlds (or worlds
                     {:localhost })])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- Multiverse
  [worlds :- (s/Maybe world-map)]
  (map->Multiverse {:worlds worlds}))
