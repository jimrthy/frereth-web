(ns ^:figwheel-load frereth.globals
    (:require [schema.core :as s :include-macros true]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; TODO: Move this into common
;; After I figure out how to get it to actually work
(def world-id #_(s/conditional s/Keyword s/Str s/Uuid) s/Any)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defonce app-state
  (atom {;; Part the renderer will use to decide what to draw
                          :worlds {:splash {:state :initializing
                                            :repl {:heading "Local"
                                                   :output []
                                                   :input "=>"
                                                   :namespace "user"
                                                   :state nil}
                                            :renderer 'frereth.three/splash-screen}}
                          ;; World to draw
                          ;; Note that, really, this needs to be controlled by
                          ;; something like an X Window Manager.
                          ;; Could very well have many visible active worlds at
                          ;; the same time.
                          ;; That doesn't seem wise, but it's definitely possible.
                          :active-world :splash
                          ;; Interaction w/ client
                          ;; Yes, this really is singular:
                          ;; renders *->1 client
                          ;; client 1->* servers
                          :channel-socket nil}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn swap-world-state!
  [which :- world-id
   f :- (s/=> s/Any s/Any)]
  (swap! app-state
         (fn [existing]
           (update-in existing [:worlds which :state] f))))

(defn get-world-state
  "Returns the current state associated w/ the specified world

Really just a helper function because I'm not crystal-clear on the shape of the
app-state atom"
  [world-key]
  (-> app-state deref :worlds world-key :state))

(defn get-active-world
  []
  (-> app-state deref :active-world))

(defn get-active-world-state
  []
  (get-world-state (get-active-world)))
