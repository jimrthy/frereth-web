(ns frereth-web.completion
  "Flag for marking everything complete

  Mainly so background threads can exit and let the
  container's GC handle permgen"
  (:require [com.stuartsierra.component :as component]
            [immutant.daemons :as daemons]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def FutureClass (-> (promise) class))
(def Finished {:done FutureClass})

(s/defrecord FinishedHandler [internal :- Finished
                              name :- s/Str]
  component/Lifecycle
  (start
   [this]
   (let [stop-fn (fn []
                   (-> internal :done (deliver true)))]
     (daemons/singleton-daemon name (constantly nil) stop-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- FinishedHandler
  [{:keys [name]
    :or {name "Frereth"}
    :as options} :- {:name s/Str}]
  (map->FinishedHandler {:internal {:done (promise)
                                    :name name}}))
