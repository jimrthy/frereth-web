(ns com.frereth.web.core
  (:require [com.stuartsierra.component :as component]
            [com.frereth.web.system :as sys]
            [immutant.daemons :as daemons]
            [ribol.core :refer (raise)])
  ;; Q: Do I want to do this?
  ;; It just seems to cause trouble
  #_(:gen-class))

(defn -main
  "This is where immutant will kick off"
  [& args]
  (let [initial (sys/ctor)
        system (component/start initial)]
    (daemons/singleton-daemon "Frereth"
                              (constantly nil)
                              (fn []
                                (component/stop system)))))
