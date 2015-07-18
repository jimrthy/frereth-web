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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn auth-loop-creator :- fr-skm/async-channel
  [this :- ConnectionManager]
  (let [auth-sock (-> this :frereth-server :auth-sock)
        auth-request (:auth-request this)
        minutes-5 (partial async/timeout (* 5 (util/minute)))]
    ;; It seems almost wasteful to start this before there's any
    ;; interest to express. But the 90% (at least) use case is for
    ;; the local server where there won't ever be any reason
    ;; for it to timeout.
    (com-comm/dealer-send! auth-sock :where-should-my-people-call-your-people)
    (async/go-loop [t-o (minutes-5)]
      (let [[v c] (async/alts! [auth-request to])]
        (if v
          (raise :not-implemented "Start Here")
          (when (= t-o c)
            (log/info "Auth Loop Creator: heartbeat")
            (recur (minutes-5)))))
      (log/warn "ConnectionManager's auth-loop exited"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- ConnectionManager
  [{:keys [url]}]
  (map->ConnectionManager {:url url}))
