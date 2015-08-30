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
              (if-let [renderer (:renderer data)]
                (do
                  (log/debug "Building up a view from non-default renderer")
                  (let [rendered (om/build renderer (:world-state data))
                        _ (println "New world rendered")
                        printable-rendered (pr-str rendered)]
                    (log/debug printable-rendered)
                    (log/debug "Adding that to the div")
                    ;; I'm pretty sure that this is causing an Invariant Violation
                    ;; because it isn't a valid ReactComponent.
                    rendered))
                (do
                  ;; state has no keys
                  ;; this is not seqable
                  ;; data *is* the cursor that I want
                  (log/debug "Just using default renderer view. Current data keys:\n"
                             (-> data keys  pr-str))
                  (dom/canvas #js {:id "view" })))
              (om/build repl/repl-wrapper (:repls data))))
    om/IDidUpdate
    (did-update
     [this prev-props prev-state]
     ;; Can't do this until after the canvas has been renderered.
     (if-let [renderer (:renderer prev-props)]
       (log/debug "Updated w/ non-default renderer:\n"
                  (pr-str (keys prev-props)))
       (do
         (log/debug "Starting 3-D graphics because we've created the view canvas\n"
                    "But not the renderer"
                    #_(pr-str js/THREE) "js/THREE is meaningful here"
                    "\nProperties: " (pr-str (keys prev-props))
                    "\nState: " (pr-str (keys prev-state)))
         (three/start-graphics js/THREE))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start
  []
  (om/root
   world-wrapper
   global/app-state
   {:target (. js/document (getElementById "everything"))}))
