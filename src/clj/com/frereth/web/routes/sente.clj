(ns com.frereth.web.routes.sente
  "Set up handlers for promoting web sockets"
  (:require [com.frereth.common.util :as util]
            [com.frereth.web.routes.websock :as ws]
            [plumbing.core :as plumbing :refer (defnk)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.frereth.web.routes.websock WebSockHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes

;; Q: What does the 200 response look like?
(defnk $chsk$GET
  {:responses {200 {:what? s/Int}}}
  [request
   [:resources [:web-sock-handler ch-sock]]]
  ;; TODO: Have client check that its login dialog isn't
  ;; expired/going to expire in, say, the next half-second.
  ;; Or whatever its server does for auth (which may be
  ;; nothing more than connecting anonymously).
  ;; This renderer will be trying to connect soon, so
  ;; the upstream pieces might as well be ready to
  ;; kick off the action.
  (log/warn "Have Client freshen login dialog")
  (let [handler (:ring-ajax-get-or-ws-handshake ch-sock)
        response (handler request)]
    response))

(defnk $chsk$POST
  {:responses {200 {}}}
  [request
   [:resources [:web-sock-handler chsk]]]
  ;; Q: When is this supposed to happen?
  (log/debug "chsk post:\n"
             (util/pretty request))
  (let [handler (:ring-ajax-post chsk)]
    (handler request)))
