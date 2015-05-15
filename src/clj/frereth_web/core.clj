(ns frereth-web.core
  (:require [com.stuartsierra.component :as component]
            [frereth-web.system :as sys]
            [ribol.core :refer (raise)])
  ;; Q: Do I want to do this?
  ;; It just seems to cause trouble
  (:gen-class))

(defn -main
  "This is where immutant will kick off"
  [& args]
  ;; This is going to fail right off the bat:
  ;; I have to specify a system descriptor
  (let [initial (sys/ctor)
        active (component/start initial)]
    (try
      ;; It looks like just starting the web server should be enough
      ;; for this part
      (raise {:not-implemented "Must return"
              :problem "Otherwise container never finishes initializing"
              :question "So how's this supposed to work?"})
      ;; Just wait for the promise to be delivered
      @(:done active)
      (finally
        ;; TODO: Need to tear down the actual servlet
        ;; There's more involved here than meets the eye
        (component/stop active)))))
