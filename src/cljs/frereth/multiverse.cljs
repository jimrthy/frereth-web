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

(s/defrecord Multiverse [initial :- world-description
                         worlds :- world-map]
  component/Lifecycle
  (start
      [this]
    (let [worlds (or worlds
                     {:localhost initial})]
      (doseq [world worlds]
        (component/start world))
      (assoc this :worlds worlds)))
  (stop
      [this]
    ;; It's tempting to dissoc everything except the
    ;; initial localhost.
    ;; That seems like valuable functionality, but it
    ;; doesn't really belong in here.
    ;; It seems to be more like the job of a SessionManager
    ;; that, really, belongs on the localhost server
    (doseq [world worlds]
      (component/stop world))
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- Multiverse
  [args
   worlds :- (s/Maybe world-map)]
  (map->Multiverse (select-keys [initial worlds])))
