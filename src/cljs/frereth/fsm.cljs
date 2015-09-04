(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [frereth.globals :as global]
            [om.core :as om]
            [ribol.cljs :refer [create-issue
                                *managers*
                                *optmap*
                                raise-loop]]
            [taoensso.timbre :as log])
  (:require-macros [ribol.cljs :refer [raise]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defmulti pre-process
  [{:keys [data] :as new-world}]
  (let [{:keys [type version]} data]
    [type version]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defmethod pre-process [:html 5]
  [{:keys [body css script] :as html}]
  (log/debug "Switching to HTML 5")
  (raise {:not-implemented "This is more complicated/dangerous than it looks at first glance"})
  (let [renderer (fn [data owner]
                   (reify
                     om/IRender
                     (render [_]
                             (log/debug "HTML 5: simplest approach I can imagine: just return " (pr-str body))
                             body)))]
    (swap! global/app-state assoc :renderer renderer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn transition-world
  [to-activate]
  (swap! (fn [current]
           (if-let [_ (-> current :worlds (get to-activate))]
             (assoc current :active-world to-activate)
             (raise {:unknown-world to-activate})))))

(defn initialize-world
  "Let's get this party started!

This is really just the way the world bootstraps.

It needs to send us enough initial info to start loading the full thing.

This should happen in response to a blank-slate request
for initialization. It should never (?) happen a second time.

Once it's complete, the world should have enough information to
finish loading via update messages.

TODO: Limit the amount of time spent here
Q: Can I do that by sticking it in a go loop, trying to alts! it
with a timeout, and then somehow cancelling the transaction if it times
out?"
  [{:keys [action-url
           expires
           name   ; should really be a centrally registered UUID. Or maybe just the domain-name:port/URL
           session-token
           world]
    :as destination}]
  (log/info "=========================================
This is important!!!!!
Initializing World:
=========================================\n"
            destination)
  (when (= destination :hold-please)
    (raise {:obsolete "Why don't I have the handshake toggle fixed?"}))
  (swap! (fn [current]
           (if (get (:worlds current) name)
             current
             (assoc-in current [:worlds name] (pre-process destination))))))
