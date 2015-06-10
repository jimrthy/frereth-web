(ns com.frereth.web.core
  (:require [com.stuartsierra.component :as component]
            [com.frereth.web.system :as sys]
            [immutant.daemons :as daemons]
            [ribol.core :refer (raise)])
  ;; Q: Do I want to do this?
  ;; It just seems to cause trouble
  (:gen-class))

(defn -main
  "This is where immutant will kick off"
  [& args]
  (let [initial (sys/ctor args "frereth.system.edn")
        system (component/start initial)]
    ;; TODO: The name here really needs to come from system's
    ;; "combine-options".
    ;; I'm just having a tough time figuring out how to make that
    ;; play nicely with the REPL
    (daemons/singleton-daemon "FIXME: Needs to come from env"
                              (constantly nil)
                              (fn []
                                (component/stop system)))))
