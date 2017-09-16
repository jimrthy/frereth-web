(ns com.frereth.web.completion
  "Flag for marking everything complete

  Mainly so background threads can exit and let the
  container's GC handle permgen

  TODO: Dump this. Use the one supplied by component-dsl"
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def FutureClass (-> (promise) class))

(def FinishedHandler {:done FutureClass})

(def FinishedCtorHandler {:complete {}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef ctor
        :args (s/cat :ignored any?)
        :ret FinishedHandler)
(defn ctor
  [_]
  (throw (ex-info "Obsolete" {:replacement "Built-in in component-dsl"}))
  {:done (promise)})
