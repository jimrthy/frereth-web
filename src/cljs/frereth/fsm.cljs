(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [frereth.globals :as global]
            [ribol.cljs :refer [create-issue
                                *managers*
                                *optmap*
                                raise-loop]]
            [taoensso.timbre :as log])
  (:require-macros [ribol.cljs :refer [raise]]))

(defn transition-to-html5
  [{:keys [body css script] :as html}]
  ;; Q: Aren't we supposed to have symbol maps that provide useful info for this sort of thing now?
  (raise {:not-implemented html
          :stack-trace "Start Transition"}))

(defn transition-world
  "Let's get this party started!"
  [{:keys [action-url
           expires
           session-token
           world]
    :as destination}]
  (log/info "=========================================
This is important!!!!!
World transition to:
=========================================\n"
            ;; LOL First time I tried this, I hit :hold-please
            destination)
  (let [data (:data destination)
        {:keys [type version]} data]
    (cond
      ;; TODO: Need an obvious way to do exactly that.
      ;; Although, this seems to be acting on something like a semaphore model
      ;; right now.
      ;; Call it once, it fails.
      ;; Succeeds the second time around.
      ;; Q: What do I have wrong with the basic server interaction?
      (= destination :hold-please) (js/alert "Don't make me call a second time")
      (and (= type :html)
           (= version 5)) (transition-to-html5 (select-keys data [:body :css :script]))
           :else (js/alert (str "Don't understand World Transition Response:\n"
                                destination
                                "\nwhich is a: " (type destination)
                                "\nTop-Level Keys:\n"
                                (keys destination)
                                "\nkeys that matter inside the data:\n"
                                (keys data)
                                "\nN.B.: data is an instance of: "
                                (comment (type data))
                                "\nSmart money says that nested data isn't getting deserialized correctly")))))
