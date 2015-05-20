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
  (let [initial (sys/ctor "frereth-web.prod.system.edn")]
    ;;; This ties is w/ a cluster-wide singleton daemon to stop on unload.
    ;;; This approach really only works when the cluster consists of just one
    ;;; server.
    ;;; TODO: Each node in the cluster really needs its own singleton-daemon name
    (component/start initial)))
