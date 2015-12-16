(ns frereth.system
  (:require [component-dsl.system :as cpt-dsl]
            [schema.core :as s])
  ;; Q: What does this look like in clojurescript?
  (:import [clojure.lang ExceptionInfo]
           [com.stuartsierra.component SystemMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- SystemMap
  "Create a newly initialized system ready to be DI'd"
  []
  (let [constructors '{:metaverse 'frereth.}
        dependencies '{}]
    (cpt-dsl/build {:structure constructors
                    :dependencies dependencies}
                   {})))
