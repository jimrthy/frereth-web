(ns com.frereth.web.routes.websock
  "For the event handlers around a sente-based web socket

Actually, it probably doesn't have to center around
sente at all."
  (:require [clojure.core.match :refer [match]]
            [com.frereth.common.util :as util]
            [com.frereth.web.routes.ring :as fr-ring]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log :refer (debugf)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord WebSockHandler [frereth-server]
  component/Lifecycle
  (start
   [this]
   this)
  (stop
   [this]
   this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Implementation

(comment
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
      (log/error "No way to send the later responses"))))

(s/defn reply
  [this :- WebSockHandler
   {:keys [?reply-fn send-fn]}
   status :- s/Int
   msg :- s/Str]
  (let [response {:status status
                  :body msg}]
    (if ?reply-fn
      (?reply-fn response)
      (if send-fn
        (send-fn response)
        (log/error "No way to send response:\n" (util/pretty response))))))

(s/defn not-found
  [this :- WebSockHandler]
  (reply this 404 "Not Found"))

(s/defn forward
  [this :- WebSockHandler
   msg]
  ;; Yes, this points out how odd my naming convention is here
  ;; TODO: Come up with a better alternative.
  ;; middleware, maybe?
  (let [client (:frereth-server this)]
    ;; This is actually the full-blown Client System
    (raise {:not-implemented "Start here"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn event-handler
  [this :- WebSockHandler
   {:keys [id ?data event] :as ev-msg}]
  (log/debugf "Event: %s\ndata: %s\nid: %s"
              event ?data id)
  (match [event ?data]
         [:frereth/pong _] (forward this ev-msg)
         :else (not-found this)))

(s/defn ctor :- WebSockHandler
  [src]
  (map->WebSockHandler src))
