(ns frereth.world-renderer
  "Big-picture world-rendering stuff"
  (:require-macros [schema.macros :as sm]
                   [schema.core :as s])
  ;; Heving this tied into rendering seems like a mistake, at best.
  (:require [frereth.repl :as repl]
            [frereth.schema :as fr-skm]
            [frereth.three :as three]
            [frereth.world-manager :refer (WorldManager)]
            [om.core :as om]
            [om.dom :as dom]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord WorldRenderer
    [manager :- WorldManager]
  component/Lifecycle
  (start
      [this]
    (om/root
     ;; This is weak. Really need to allow multiple
     ;; worlds at once.
     ;; But it's a start
     (:current manager)
     app-state
     {:target (. js/document (getElementById "everything"))})
    this)
  (stop
      [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn static-body
  [data]
  (om/component data))

(defn world-wrapper
  [data owner]
  (reify
    om/IRenderState
    (render-state
     [this state]
     (dom/div #js {:id "World"}
              (let [active-world-key (:active-world data)
                    active-world (get data active-world-key)]
                (if-let [renderer (:renderer active-world)]
                  (do
                    (log/debug "Building up a view from custom renderer")
                    (let [rendered (om/build renderer (:state active-world))
                          _ (log/debug "New world rendered")
                          printable-rendered (pr-str rendered)]
                      (log/debug printable-rendered)
                      (log/debug "Adding that to the div")
                      ;; I'm pretty sure that this is causing an Invariant Violation
                      ;; because it isn't a valid ReactComponent.
                      rendered))
                  (do
                    (log/debug "Drawing default screen. Current data keys:\n"
                               (-> data keys  pr-str))
                    (if-let [body (:body active-world)]
                      (do
                        (log/debug "Have a body")
                        (dom/div nil
                                 ;; This is really the background
                                 ;; Q: What does this part actually look like?
                                 (om/build static-body body)
                                 ;; TODO: Position this over that background
                                 ;; Although, honestly, there are a lot of times we won't need it.
                                 (dom/canvas #js {:id "view"})))
                      ;; TODO: If there's a...what?
                      ;; something like a renderer/body namespace attached to the compiled
                      ;; script, treat that as a Component that we can om/build
                      (do
                        (log/debug "Falling back to just drawing a plain canvas")
                        (dom/canvas #js {:id "view"}))))))
              (om/build repl/repl-wrapper (:repls data))))

    om/IDidMount
    (did-mount
     [this]
     ;; Can't do this until after the canvas has been renderered.
     ;; And, honestly, we should really only do it the first time around
     ;; Q: Would IDidMount be more appropriate?
     (log/debug "Starting 3-D graphics because we've created the view canvas\n"
                "But not the renderer")
     ;; It's very tempting to get the DOM node from owner and traverse it to
     ;; the canvas instead of having start-graphics locate it by ID.
     ;; That seems like it would make for looser coupling, but that also
     ;; feels misleading
     (three/start-graphics js/THREE))

    om/IWillUnmount
    (will-unmount
     [this]
     ;; TODO: Signal the splash renderer to just quit
     ;; Just add an atom to local state that it can
     ;; check before it tries to draw another frame
     (log/debug "World Wrapper will unmount"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  ([manager]
   (map->WorldRenderer {:manager manager}))
  ([]
   (->WorldRenderer)))
