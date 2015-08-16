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
  ;; This is the reason I haven't managed to get this
  ;; approach to work before.
  ;; Q: How do I access the full request?
  ;; Q: Alternatively, do I really need to?
  (let [handler (:ring-ajax-get-or-ws-handshake ch-sock)
        response (handler request)]
    (log/debug "sente's RING ws handshake response:\n"
               (util/pretty response)
               "\nfrom Handler:" handler)
    response))

(defnk $chsk$POST
  {:responses {200 {}}}
  [request
   [:resources [:web-sock-handler chsk]]]
  (let [handler (:ring-ajax-post chsk)]
    (handler request)))
