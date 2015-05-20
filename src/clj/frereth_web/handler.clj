(ns frereth-web.handler
  "This is where the web server lives"
  (:require [com.stuartsierra.component :as component]
            [frereth-web.routes :as routes]
            [immutant.web :as web]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth_web.routes Routes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ServerDescription {:router Routes})

(s/defrecord Server [router :- Routes]
    component/Lifecycle
  (start
   [this]
   (let [server-options (web/run router)]
     (into this {:killer server-options})))

  (stop
   [this]
   (let [killer (:killer this)]
     (web/stop killer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- Server
  [options :- ServerDescription]
  (map->Server ServerDescription))
