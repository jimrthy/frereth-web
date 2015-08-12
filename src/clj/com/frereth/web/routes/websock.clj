(ns com.frereth.web.routes.websock
  "For the event handlers around a sente-based web socket

Actually, it probably doesn't have to center around
sente at all."
  (:require [clojure.core.async :as async]
            [clojure.core.match :refer [match]]
            [com.frereth.client.connection-manager :as con-man]
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

(s/defn ^:always-validate reply
  [this :- WebSockHandler   ; Q: Will there ever be any reason for this?
   {:keys [?reply-fn send-fn id]}
   event-key :- s/Keyword
   event-data :- s/Any]
  (let [response [event-key event-data]]
    (if ?reply-fn
      (?reply-fn response)
      ;; The id is really the event key. It's useless here.
      (send-fn id response))))

(s/defn not-found
  [this :- WebSockHandler
   ev-msg]
  (reply this ev-msg :http/not-found {:status 404 :body "\"Not Found\""}))

(s/defn forward
  [this :- WebSockHandler
   {:keys [send-fn] :as msg}]
  (raise :not-implemented))

(s/defn initiate-auth!
  [this :- WebSockHandler
   ev-msg]
  (async/thread
    (let [cpt (-> this :frereth-server :connection-manager)
          responder (con-man/initiate-handshake cpt 5 2000)
          response-chan (:respond responder)
          [v c] (async/alts!! [response-chan (async/timeout 1000)])]
      (if v
        (do
          (log/debug "Initiating handshake w/ Server returned:\n" (util/pretty v))
          (reply this ev-msg :http/ok {:status 200 :body v}))
        (let [msg (if (= c response-chan)
                    "Handshaker closed response channel. This is bad."
                    "Timed out waiting for response. This isn't great")]
          (reply this ev-msg :http/internal-error {:status 500 :body (pr-str msg)}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn event-handler
  [this :- WebSockHandler
   {:keys [id ?data event] :as ev-msg}]
  (log/debug "Event: " event
             " Data: " ?data
             " ID: " id)
  (match [event ?data]
         [:frereth/blank-slate _] (initiate-auth! this ev-msg)
         [:frereth/pong _] (forward this ev-msg)
         :else (not-found this ev-msg)))

(s/defn ctor :- WebSockHandler
  [src]
  (map->WebSockHandler src))
