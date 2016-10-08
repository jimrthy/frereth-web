(ns com.frereth.web.handlers.sente
  "Handler implementations for sente connections"
  (:require [clojure.spec :as s]
            [ring.util.response :as res]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (get-sch-adapter)]))

;;; Under fnhouse, I could add the result from
;;; make-channel-socket! to the parameters supplied
;;; to the handler.
;;; I still could, given the appropriate middleware
;;; manipulations.
;;; It's tempting to handle this as a component.
;;; For now, just mimic the sente README
(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (defn channel-sock
    [{:keys [request-method] :as req}]
    (condp = request-method
      :post (ajax-post-fn req)
      ;; This next comment is really pretty rotten.
      ;; The idea behind it is obsolete: the login dialog
      ;; should just be another app (albeit with very wide-
      ;; ranging consequences) that doesn't involve anything
      ;; special at this level.
      ;; Keeping it around until I have time to tackle that
      ;; side of things.
      ;; Just tackling one thing at a time.

      ;; TODO: Have client check that its login dialog isn't
      ;; expired/going to expire in, say, the next half-second.
      ;; Or whatever its server does for auth (which may be
      ;; nothing more than connecting anonymously).
      ;; This renderer will be trying to connect soon, so
      ;; the upstream pieces might as well be ready to
      ;; kick off the action.
      :get (ajax-get-or-ws-handshake-fn req))))
