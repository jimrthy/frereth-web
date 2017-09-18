(ns com.frereth.web.handler
  "This is where the web server lives"
  (:require [clojure.spec.alpha :as s]
            [com.frereth.web.routes.core :as routes]
            [com.frereth.web.routes.websock]
            [immutant.web :as web]
            [integrant.core :as ig]
            [hara.event :refer (manage raise)]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;; What the Server's ctor needs/accepts
(s/def ::server-description map?)

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
;;; Component

(s/def ::http-routes :com.frereth.web.routes.core/http-routes)
(s/def ::killer (s/fspec :args nil :ret any?))
(s/def ::server-options map?)
;; TODO: Document the schema that Immutant returns
;; from web/run that is being stored in here
(s/def ::server (s/keys :req [::http-router
                              ::killer
                              ::server-options]))

(defmethod ig/init-key ::server
  [_ {:keys [::http-router]
      :as this}]
  ;; TODO: Ditch the magic numbers. Pull config from a config file/envvar
  ;; c.f. weavejester environ
  (let [server-options (web/run (:http-router http-router) {:host "0.0.0.0"
                                                            :port 8093})]
    (into this {::server-options server-options   ; really just for REPL access
                ::killer (create-stopper server-options)})))

;;; TODO: Odds are, pause/resume overrides would be useful

(defmethod ig/halt-key! ::server
  [_ {:keys [::killer]}]
  (when killer
    (killer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public
