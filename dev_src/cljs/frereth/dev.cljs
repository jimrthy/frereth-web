(ns frereth.dev
    (:require
     [frereth.core]
     [frereth.globals :as global]
     ;; This has been obsolete for a good long while now.
     ;; Q: How is this supposed to be done these days?
     #_[figwheel.client :as fw]))

(enable-console-print!)

(comment
  (fw/start {
             :websocket-url "ws://localhost:3449/figwheel-ws"
             :on-jsload (fn []
                          (println "Loaded")
                          ;; (stop-and-start-my app)
                          )}))
(throw (ex-info "Not Implemented"
                {:problem "Need to load figwheel, in dev mode"
                 :for-that-matter "Really ought to load devcards,
if there's a magic element there to tell me to do so"}))
