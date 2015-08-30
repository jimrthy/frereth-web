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
                (om/build renderer (:world-state state))
                (dom/canvas #js {:id "view" }))
              (om/build repl/repl-wrapper (:repls state))))
    om/IDidUpdate
    (did-update
     [this prev-props prev-state]
     ;; Can't do this until after the canvas has been renderered.
     ;; Part of the reason I set this up the way I did originally
     ;; was that my access to js/THREE was bolloxed inside functions:
     ;; I only had access to it at the top level
     ;; Q: Has that been resolved?
     (log/debug "Starting 3-D graphics because we've created the view canvas:\n"
                #_(pr-str js/THREE) "js/THREE is meaningful here"
                "\nProperties: " (pr-str (keys prev-props))
                "\nState: " (pr-str (keys prev-state)))
     (three/start-graphics js/THREE))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start
  []
  (om/root
   world-wrapper
   global/app-state
   {:target (. js/document (getElementById "everything"))}))
