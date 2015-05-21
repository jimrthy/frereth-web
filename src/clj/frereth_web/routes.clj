(ns frereth-web.routes
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [com.stuartsierra.component :as component]
            [frereth-server.comms :as comms]
            [ribol.core :refer [raise]]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth_server.comms Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def StandardDescription {})
(def StandardCtorDescription {:http-router StandardDescription})
(def WebSocketDescription {})

;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord HttpRoutes [frereth-server :- Connection
                         ;; This is a function that takes 1
                         ;; arg and returns a Ring response.
                         ;; TODO: Spec that schema
                         http-router]
  component/Lifecycle
  (start
   [this]
   (let [handler (fn [req]
                   {:status 200
                    :body "Hello world!"})]
     (assoc this :http-router handler)))
  (stop
   [this]
   (assoc this :handler nil)))

(def UnstartedHttpRoutes (assoc StandardDescription
                                :http-router s/Any
                                :frereth-server s/Any))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- UnstartedHttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
