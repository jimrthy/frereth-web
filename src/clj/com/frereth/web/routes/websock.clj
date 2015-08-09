(ns com.frereth.web.routes.websock
  "For the event handlers around a sente-based web socket

Actually, it probably doesn't have to center around
sente at all."
  (:require [com.frereth.web.routes.ring :as fr-ring]
            [schema.core :as s]
            [taoensso.timbre :as log :refer (debugf)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defmulti event-msg-handler* :id)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(defmethod event-msg-handler* :default  ; fallback
  [{:keys [event id ?data ring-req ?reply-fn send-fn]
    :as ev-msg}]
  (let [uid (fr-ring/extract-user-id ring-req)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:no-server-handler event}))))

(defmethod event-msg-handler* :frereth/pong
  [ev-msg]
  (log/info "Received a pong"))

(defmethod event-msg-handler* :frereth/ready
  [{:keys [event id ?data ?reply-fn ring-req send-fn]}]
  (when ?reply-fn
    (?reply-fn {:frereth/ack "Notifying Server"}))
  (if send-fn
    (let [uid (fr-ring/extract-user-id ring-req)
          ;; Really need access to something that I
          ;; can use to forward the request along to
          ;; the server.
          ;; That something needs to validate that this
          ;; at least looks like a legitimate request.
          ;; It's tempting to just add the channel that's
          ;; the entryway to the EventLoopInterface and
          ;; send this in a go block.
          ;; But this really isn't a trusted connection,
          ;; and I don't want to short-circuit any
          ;; future attempts at security.
          ;; This really needs to be part of a
          ;; Component that just drops these messages
          ;; into an event dispatcher and moves along
          response {:frereth/not-implemented "This approach won't work"}]
      (send-fn response))
    (log/error "No way to send the later responses")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn event-handler
  [{:keys [id ?data event] :as ev-msg}]
  (log/debugf "Event: %s\ndata: %s\nid: %s"
              event ?data id)
  (event-msg-handler* ev-msg))
