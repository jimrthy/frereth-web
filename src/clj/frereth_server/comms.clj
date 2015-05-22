(ns frereth-server.comms
  "Interact with 'real' server(s)

  TODO: This is going to be using 0mq"
  (:require [com.stuartsierra.component :as component]
            [frereth-web.completion :as completed]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ConnectionDescription {:address s/Str
                            :protocol s/Str
                            :port s/Int})

(def ConnectionCtorDescription {:frereth-server ConnectionDescription})

(s/defrecord Connection [complete :- completed/FinishedHandler
                         address :- s/Str
                         protocol :- s/Str
                         port :- s/Int]
  component/Lifecycle
  (start
   [this]
   (log/error "TODO: Create a...actually, we're probably going to need one socket per connection")
   (comment (raise :not-implemented))
   this)
  (stop
   [this]
   (log/error "TODO: Close all the sockets. Should probably warn the other side")
   (comment (raise :not-implemented))
   this))

(def UnstartedConnection (into ConnectionDescription
                               ;; As it stands, this will be nil until Components
                               ;; has its chance to work its magic
                               {:complete s/Any}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- UnstartedConnection
  [opts :- ConnectionDescription]
  (map->Connection opts))

