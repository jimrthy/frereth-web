(ns ^:figwheel-load frereth.globals
    (:require [frereth.schema :as fr-skm]
              [schema.core :as s :include-macros true]
              [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def repl-state
  "Pieces involved in a world's REPL
This is probably overly simplistic, but it's a start"
  {:heading s/Str
   :output [s/Str]
   :input s/Str
   :state fr-skm/compiler-black-box})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;; Original version of the initial splash screen world:
(comment {:splash {:state :initializing
                   :repl {:heading "Local"
                          :output []
                          :input "=>"
                          :namespace "user"
                          :state nil}
                   :renderer 'frereth.three/splash-screen}})
(defonce app-state
  (atom {;; Part the renderer will use to decide what to draw
                          :worlds {}
                          ;; World to draw
                          ;; Note that, really, this needs to be controlled by
                          ;; something like an X Window Manager.
                          ;; Could very well have many visible active worlds at
                          ;; the same time.
                          ;; That doesn't seem wise, but it's definitely possible.
                          :active-world nil
                          ;; Interaction w/ client
                          ;; Yes, this really is singular:
                          ;; renders *->1 client
                          ;; client 1->* servers
                          :channel-socket nil}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn swap-world-state!
  [which :- fr-skm/world-id
   f :- (s/=> s/Any s/Any)]
  (swap! app-state
         (fn [existing]
           (update-in existing [:worlds which :state] f))))

(defn get-world-state
  "Returns the current state associated w/ the specified world

Really just a helper function because I'm not crystal-clear on the shape of the
app-state atom"
  [world-key]
  (-> app-state deref :worlds (get world-key) :state))

(s/defn get-world-repl :- repl-state
  [world-key :- fr-skm/world-id]
  (-> app-state deref :worlds (get world-key) :repl))

(s/defn get-compiler-state
  [world-key :- fr-skm/world-id]
  (:state (get-world-repl world-key)))

(defn get-active-world
  []
  (-> app-state deref :active-world))

(s/defn set-active-world!
  [world-id :- fr-skm/world-id]
  (log/debug "Trying to activate world: '" (pr-str world-id) "'")
  (swap! app-state (fn [current]
                     (if-let [_ (-> current :worlds (get world-id))]
                       (assoc current :active-world world-id)
                       (raise {:unknown-world world-id})))))

(defn get-active-world-state
  []
  (get-world-state (get-active-world)))

(s/defn add-world!
  ([{:keys [compiler-state url] :as template} :- fr-skm/world-template
     state :- s/Any]
   (let [world-id (:world-id template)]
     (swap! app-state
            (fn [existing]
              (let [worlds (:worlds existing)]
                (when-let [already-added (get worlds world-id)]
                  (log/error "Duplicate world id: '" (pr-str world-id)
                             "' in: " (-> app-state deref :worlds keys))
                  (raise {:duplicate-world world-id
                          :url url
                          :state state
                          :compiler-state compiler-state}))
                (let [fresh {:state state
                             :repl {:heading (pr-str url)
                                    :output []
                                    :input "=>"
                                    :state compiler-state}}]
                  (update-in existing [:worlds world-id] (constantly fresh))))))))
  ([template :- fr-skm/world-template]
   (add-world! template :initializing)))
