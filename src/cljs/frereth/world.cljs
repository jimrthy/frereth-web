(ns frereth.world
  "Big-picture world-rendering stuff"
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                   [schema.macros :as sm]
                   [schema.core :as s])
  (:require [frereth.globals :as global]
            [frereth.repl :as repl]
            [frereth.three :as three]
            [om.core :as om]
            [om.dom :as dom]
            [taoensso.timbre :as log]))

;; FIXME: Debug only
(enable-console-print!)

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
                        (dom/div nil
                                 ;; This is really the background
                                 ;; Q: What does this part actually look like?
                                 (om/build body (:state active-world))
                                 ;; TODO: Position this over that background
                                 ;; Although, honestly, there are a lot of times we won't need it.
                                 (dom/canvas #js {:id "view"})))
                      (dom/canvas #js {:id "view"})))))
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

(defn start
  []
  (om/root
   world-wrapper
   global/app-state
   {:target (. js/document (getElementById "everything"))}))
