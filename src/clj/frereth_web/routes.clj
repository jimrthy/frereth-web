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
(def WebSocketDescription {})

;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord Routes [server :- Connection]
  component/Lifecycle
  (start
   [this]
   (raise :not-implemented))
  (stop
   [this]
   (raise :not-implemented)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- Routes
  []
  (raise :not-implemented))
