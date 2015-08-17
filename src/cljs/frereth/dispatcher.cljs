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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn handle!
  [{:keys [send-fn event id data? state] :as message-batch}]
  (log/debug "Incoming message-batch:\n"
             (keys message-batch)
             "\nEvent: " event
             "\nID: " id
             "\nData: " data?
             "\nState: " state)
  ;; This is a cheese-ball dispatching mechanism, but
  ;; anything more involved is YAGNI

  (cond
    (= id :chsk/handshake) (do (log/info "Channel Socket Handshake received")
                              (log/error "TODO: Update the splash screen animation"))
    :else (let [cleaned-request (dissoc message-batch :ch-recv :send-fn :state)
                response (not-found cleaned-request)]
            (log/warn "Don't have a handler for:\n" cleaned-request)
            (send-fn [:frereth/response response] 5000 (partial standard-cb-notification response)))))
