(ns frereth-web.completion
  "Flag for marking everything complete

  Mainly so background threads can exit and let the
  container's GC handle permgen"
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def FutureClass (-> (promise) class))

(def FinishedHandler {:done FutureClass})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- FinishedHandler
  [_ :- {}]
  {:done (promise)})
