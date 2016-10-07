(ns com.frereth.web.routes.sente
  "Set up handlers for promoting web sockets"
  (:require [com.frereth.common.util :as util]
            [com.frereth.web.routes.websock :as ws]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes

;; Q: What does the 200 response look like?
(defn $chsk$GET
  [request
   #_[:resources [:web-sock-handler ch-sock]]
   channel-socket]
  (throw (ex-info "Not Implemented" {:todo "Convert to bidi"}))
  {:responses {200 {:what? integer?}}}
  ;; TODO: Have client check that its login dialog isn't
  ;; expired/going to expire in, say, the next half-second.
  ;; Or whatever its server does for auth (which may be
  ;; nothing more than connecting anonymously).
  ;; This renderer will be trying to connect soon, so
  ;; the upstream pieces might as well be ready to
  ;; kick off the action.
  (log/warn "Have Client freshen login dialog")
  (let [handler (:ring-ajax-get-or-ws-handshake channel-socket)
        response (handler request)]
    response))

(defn $chsk$POST
  [request
   #_[:resources [:web-sock-handler chsk]]
   chsk]
  (throw (ex-info "Not Implemented" {:todo "Convert to bidi"}))
  {:responses {200 {}}}
  ;; Q: When is this supposed to happen?
  (log/debug "chsk post:\n"
             (util/pretty request))
  (let [handler (:ring-ajax-post chsk)]
    (handler request)))
