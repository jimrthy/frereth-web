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
   :request request})

(defn standard-cb-notification
  [sent-message
   cb-reply]
  (log/debug "Received\n" cb-reply
             "\nin response to:\n" sent-message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn handle!
  [message
   {:keys [send!] :as chsk}]
  (cond
    :else (let [msg (not-found message)]
            (send! [:frereth/response msg] 5000 (partial standard-cb-notification msg)))))
