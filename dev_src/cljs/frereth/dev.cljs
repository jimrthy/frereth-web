(ns frereth.dev
    (:require
     [frereth.core]
     [frereth.globals :as global]
     [figwheel.client :as fw]))

(enable-console-print!)

(fw/start {
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :on-jsload (fn []
               (println "Loaded")
               ;; (stop-and-start-my app)
               )})
