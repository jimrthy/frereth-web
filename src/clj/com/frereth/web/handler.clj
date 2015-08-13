(ns com.frereth.web.handler
  "This is where the web server lives"
  (:require [com.stuartsierra.component :as component]
            [com.frereth.web.routes.core :as routes]
            [immutant.web :as web]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.frereth.web.routes.core HttpRoutes]
           [com.frereth.web.routes.websock WebSockHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def ServerDescription
  "What the Server's ctor needs/accepts"
  {})

(declare create-stopper)
(s/defrecord Server [http-router :- (s/maybe HttpRoutes)
                     killer :- (s/maybe (s/=> s/Any))
                     ;; TODO: Document the schema that Immutant returns
                     ;; from web/run that is being stored in here
                     server-options :- (s/maybe {s/Any s/Any})]
    component/Lifecycle
  (start
   [this]
   (when-not (:killer this)
     ;; TODO: Ditch the magic numbers. Pull config from a config file/envvar
     (let [server-options (web/run (:http-router http-router) {:host "0.0.0.0"
                                                               :port 8093})]
       (into this {:server-options server-options   ; really just for REPL access
                   :killer (create-stopper server-options)}))))
  (stop
   [this]
   (when-let [killer (:killer this)]
     (killer)
     (assoc this
          :killer nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn create-stopper
  "Start the web server. Return a callback to stop everything.

Probably misnamed.

When I wrote this, I was hoping it would be a convenient place to
handle the stopping callback. Which still doesn't seem to exist."
  [start-options]
  (fn []
    ;; TODO: Put other stopping side-effects into here
    (web/stop start-options)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- Server
  [options :- ServerDescription]
  (map->Server options))
