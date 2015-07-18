(ns com.frereth.web.connection-manager
  "Sets up basic auth w/ a server

Honestly, this belongs in the Client."
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.schema :as fr-skm]
            [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.stuartsierra.component SystemManager]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare auth-loop-creator)
(s/defrecord ConnectionManager
    [auth-loop :- fr-skm/async-channel
     auth-loop-stopper :- fr-skm/async-channel
     auth-request :- fr-skm/async-channel
     frereth-server :- SystemManager]
  component/Lifecycle
  (start
   [this]
   (let [auth-loop-stopper (async/chan)
         auth-request (async/chan)
         almost (assoc this
                       :auth-loop-stopper auth-loop-stopper
                       :auth-request auth-request)
         auth-loop (auth-loop-creator almost)]
     (assoc almost :auth-loop this)))
  (stop
   [this]
   this))

(def auth-dialog-description {:expires DateTime
                              :client-script s/Str})
(def optional-auth-dialog-description (s/Maybe auth-dialog-description))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn request-auth-descr
  "Signal the server that we want to get in touch"
  [auth-sock :- mq/Socket]
  (com-comm/dealer-send! auth-sock :where-should-my-people-call-your-people))

(s/defn unexpired-auth-description :- optional-auth-dialog-description
  [this :- ConnectionManager
   potential-description :- optional-auth-dialog-description]
  "If we're missing the description, or it's expired, try to read a more recent"
  (let [now (DateTime.)]
    (if (or (not potential-description)
            (< now (:expires potential-description)))
      (let [auth-sock (-> :frereth-server :auth-sock)]
        (if-let [description-frames (com-comm/dealer-recv! auth-sock)]
          ;; Note that this newly received description could also be expired already
          (translate-description-frames description-frames)  ; Previous request triggered this
          (request-auth-descr auth-sock)))  ; trigger another request
      potential-description)))  ; Last received version still good

(s/defn ^:always-validate send-auth-descr-response!
  [{:keys [respond]}
   current-description :- auth-dialog-description]
  (async/>!! respond current-description))

(s/defn ^:always-validate send-wait!
  [{:keys [respond]}]
  (async/>!! respond :hold-please))

(s/defn dispatch-auth-request :- s/Any
  [this :- ConnectionManager
   v
   c :- fr-skm/async-channel
   current-description :- optional-auth-dialog-description]
  (when-not (= c (:auth-request this))
    (raise {:problem "Auth request on unexpected channel"
            :details {:value v
                      :channel c}
            :possibilities this}))
  (if-let [current-description (unexpired-auth-description this current-description)]
    (send-auth-descr-response! v current-description)
    (send-wait! v)))

(s/defn auth-loop-creator :- fr-skm/async-channel
  [{:keys [auth-request frereth-server]
    :as this} :- ConnectionManager]
  (let [auth-sock (:auth-sock frereth-server)
        auth-request (:auth-request this)
        minutes-5 (partial async/timeout (* 5 (util/minute)))]
    ;; It seems almost wasteful to start this before there's any
    ;; interest to express. But the 90% (at least) use case is for
    ;; the local server where there won't ever be any reason
    ;; for it to timeout.
    (request-auth-descr auth-sock)
    (async/go-loop [t-o (minutes-5)
                    dialog-description nil]
      (let [[v c] (async/alts! [auth-request to])]
        (if v
          (let [dialog-description (dispatch-auth-request this v c dialog-description)]
            (recur (minutes-5) dialog-description))
          (when (= t-o c)
            (log/info "Auth Loop Creator: heartbeat")
            (recur (minutes-5) dialog-description))))
      (log/warn "ConnectionManager's auth-loop exited"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate initiate-handshake :- optional-auth-dialog-description
  [this :- ConnectionManager
   attempts :- s/Int
   timeout-ms :- s/Int]
  (let [receiver (async/chan)
        responder {:respond receiver}
        transmitter (:auth-request this)]
    (loop [remaining-attempts attempts]
      (when (< 0 remaining-attempts)
        (let [[v c] (async/alts!! [[transmitter responder] (async/timeout timeout-ms)])]
          (if v
            (if (= v :hold-please)
              (do
                (log/info "Request for AUTH dialog ACK'd. Waiting...")
                (recur (dec remaining-attempts)))
              v)
            (if (= c transmitter)
              (log/error "Auth channel closed")
              (do
                (log/warn "Timed out trying to transmit request for AUTH dialog.\n"
                          (dec remaining-attempts) " attempts remaining")
                (recur (dec remaining-attempts))))))))))

(s/defn ctor :- ConnectionManager
  [{:keys [url]}]
  (map->ConnectionManager {:url url}))
