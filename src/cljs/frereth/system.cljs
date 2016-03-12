(ns frereth.system
  "Foundation based on the Stuart Sierra Reloaded/Component framework

Or maybe the ligaments. This is where all the boot-time Components
get declared and wired together"
  (:require [com.stuartsierra.component :refer (SystemMap)]
            [component-dsl.system :as cpt-dsl]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- SystemMap
  "Create a newly initialized system ready to be DI'd"
  []
  (let [constructors '{:multiverse frereth.multiverse/ctor
                       :top-renderer frereth.world-renderer/ctor
                       :world-manager frereth.world-manager/ctor}
        dependencies {:top-renderer {:manager :world-manager}
                      :world-manager {:worlds :multiverse}}]
    (cpt-dsl/build {:structure constructors
                    :dependencies dependencies}
                   {})))
