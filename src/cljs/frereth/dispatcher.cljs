(ns frereth.dispatcher
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                     [schema.macros :as sm]
                     )
  (:require [cljs.core.async :as async]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def return-codes
  "It's very tempting to just return a ring response here"
  {:status schema.core/Int
   :body s/Any
   :request s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Named "Constants"

(def event-response-timeout-ms 5000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn not-found
  [request]
  {:status 404
   :body "\"Not Found\""
   :request (dissoc request :ch-recv :send-fn)})

(defn standard-cb-notification
  [sent-message
   cb-reply]
  (log/debug "Received\n" cb-reply
             "\nin response to:\n" sent-message))

(defn send-standard-event
  "Q: How realistic is this approach?
It works for basic request/response pairs, but fails in the
general case of ordinary event dispatching.

It almost seems like it would be better to just write everything
from the 'ordinary event dispatching' stand-point to keep the entire
approach unified"
  [send-fn
   event-type
   event-data]
  (let [event [event-type event-data]]
    (send-fn event event-response-timeout-ms (partial standard-cb-notification event))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn handle!
  [{:keys [send-fn event id ?data state] :as message-batch}]
  (log/debug "Incoming message-batch:\n"
             (keys message-batch)
             "\nEvent: " event
             "\nID: " id
             "\nData: " ?data
             "\nState: " state)

  ;; This is a cheese-ball dispatching mechanism, but
  ;; anything more involved is YAGNI
  (cond
    (= id :chsk/handshake) (do (log/info "Channel Socket Handshake received")
                              (log/error "TODO: Update the splash screen animation")
                              (send-standard-event send-fn :frereth/blank-slate {}))
    (= id :chsk/state) (log/info "ChannelSock State message received:\n"
                                 ?data)
    ;; Letting the server just take control here would be a horrible idea
    (= id :frereth/initialize-world (do
                                      (log/info "Switching to new world")
                                      ;; This is wreaking havoc
                                      ;; Q: Why?
                                      (log/error "TODO: Make this happen")))
    (= id :frereth/response (do
                              (log/debug "Request response received:\n"
                                         "I should try to do something intelligent with this,\n"
                                         "but it should really be handled in its own callback")))
    :else (let [cleaned-request (dissoc message-batch :ch-recv :send-fn :state)
                response (not-found cleaned-request)]
            (log/warn "Don't have a handler for:\n" cleaned-request)
            (send-standard-event send-fn :frereth/response response))))
