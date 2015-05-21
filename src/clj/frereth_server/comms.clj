(ns frereth-server.comms
  "Interact with 'real' server(s)

  TODO: This is going to be using 0mq"
  (:require [com.stuartsierra.component :as component]
            [frereth-web.completion :as completed]
            [ribol.core :refer (raise)]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ConnectionDescription {})

(s/defrecord Connection [complete :- completed/FinishedHandler
                         url :- ConnectionDescription]
  component/Lifecycle
  (start
   [this]
   (raise :not-implemented))
  (stop
   [this]
   (raise :not-implemented)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- Connection
  [opts :- {:url ConnectionDescription}]
  (map->Connection opts))

