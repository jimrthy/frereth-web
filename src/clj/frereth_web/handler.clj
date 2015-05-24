(ns frereth-web.handler
  "This is where the web server lives"
  (:require [com.stuartsierra.component :as component]
            [frereth-web.routes.core :as routes]
            [immutant.web :as web]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth_web.routes HttpRoutes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ServerDescription
  "What the Server's ctor needs"
  {})
(def ServerCtorDescription
  "What the system options piece looks like"
  {:web-server ServerDescription})

(s/defrecord Server [http-router :- HttpRoutes]
    component/Lifecycle
  (start
   [this]
   (let [server-options (web/run (:http-router http-router))]
     (into this {:killer server-options})))

  (stop
   [this]
   (let [killer (:killer this)]
     (web/stop killer))))

(def UnstartedServer (assoc ServerDescription
                            :http-router s/Any))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- UnstartedServer
  [options :- ServerDescription]
  (map->Server options))
