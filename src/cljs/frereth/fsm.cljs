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
  (raise {:not-implemented html}))

(defn transition-world
  "Far too much information is getting here.
TODO: Client needs to prune most of it.
This part really only cares about the world"
  [{:keys [action-url
           expires
           session-token
           world]}]
  (let [data (:data world)
        {:keys [type version]} data]
    (cond
      (and (= type :html)
           (= version 5)) (transition-to-html5 (select-keys data [:body :css :script])))))
