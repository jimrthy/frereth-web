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

(defn world-wrapper
  [data owner]
  (reify
    om/IRenderState
    (render-state
     [this state]
     (dom/div nil
              (if-let [renderer (:renderer state)]
                (do
                  ;; This isn't a particularly good approach.
                  ;; But it's a start
                  (dom/canvas #js {:id "view"})
                  ;; It's tempting to do
                  #_(comment (renderer (:world-state state)))
                  ;; here, but this should (at least theoretically)
                  ;; really only happen once, or whenever the
                  ;; global state changes.
                  ;; Actually, that's far too often.
                  ;; Still need more hammock time on this.
                  )
                (dom/div nil "Initializing..."))
              (om/build repl/repl-wrapper (:repls state))))
    om/IDidUpdate
    (did-update
     [this prev-props prev-state]
     ;; Can't do this until after the canvas has been renderered.
     ;; TODO: Absolutely does not belong in the REPL namespace.
     (log/debug "Starting 3-D graphics")
     (three/start-graphics js/THREE))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start
  []
  (om/root
   world-wrapper
   global/app-state
   {:target (. js/document (getElementById "everything"))}))
