(ns ^:figwheel-load frereth.globals
    (:require [ribol.cljs :refer [create-issue
                                  *managers*
                                  *optmap*
                                  raise-loop]]
              [schema.core :as s :include-macros true])
    ;; Q: Could I simplify this by just using :include-macros above?
    (:require-macros [ribol.cljs :refer [raise]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; TODO: Move these into common

;; After I figure out how to get it to actually work
(def world-id #_(s/conditional s/Keyword s/Str s/Uuid) s/Any)

(def zmq-protocols (s/enum :tcp))

(def world-url {:protocol zmq-protocols
                ;; At first, at least, address will almost
                ;; always be a dotted quad.
                ;; DNS should get involved soon enough
                ;; Even then...it's still host.subdomain...domain.tld
                :address s/Str
                :port s/Int
                :path s/Str})

(def compiler-black-box
  "Just so I have a label for context"
  s/Any)

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
  (-> app-state deref :worlds (get world-key) :state))

(defn get-active-world
  []
  (-> app-state deref :active-world))

(defn get-active-world-state
  []
  (get-world-state (get-active-world)))

(s/defn add-world!
  ([world-id :- world-id
     url :- world-url
     state :- s/Any
    compiler-state :- compiler-black-box]
   (swap! app-state
          (fn [existing]
            (let [worlds (:worlds existing)]
              (when-let [already-added (get worlds world-id)]
                (raise {:duplicate-world world-id
                        :url url
                        :state state
                        :compiler-state compiler-state}))
              (let [fresh {:state state
                           :repl {:heading (pr-str url)
                                  :output []
                                  :input "=>"
                                  :state compiler-state}}]
                (update-in existing [:worlds world-id] (constantly fresh)))))))
  ([world-id :- world-id
    url :- world-url
    compiler-state :- compiler-black-box]
   (add-world! world-id url :initializing compiler-state)))
