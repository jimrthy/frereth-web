(ns frereth-web.handler
  (:require [com.stuartsierra.component :as cpt]
            [frereth-web.routes :as routes]
            [immutant.web :as web]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth-web.routes Routes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ServerDescription {router Routes})

(s/defrecord Server [router :- Routes]
    comporent/Lifecycle
  (start
   [this]
   (let [stop-signaller (fn []
                          (raise {:nowhere-to-register-this}))]
     (let [base-server-options (web/run router)]
       (into this {:killer server-options}))))

  (stop
   [this]
   (let [killer (:killer this)]
     (web/stop killer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [options :- ServerDescription]
  (map->Server ServerDescription))
