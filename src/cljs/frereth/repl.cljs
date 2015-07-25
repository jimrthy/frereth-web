(ns frereth.repl
  (:require #_[cljs.js :as compiler]
            [om.core :as om]
            [om.dom :as dom]
            #_[schema.core :as s :include-macros true]))

;;;; The absolte dumbest REPL implementation that comes to mind

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defonce app-state (atom {:text "Om up and running",
                          :compiler-state (comment (compiler/empty-state))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start
  []
  (om/root
   (fn [data owner]
     (reify
       om/IRender
       (render [_]
               (dom/p nil (:text data)))))
   app-state
   {:target (. js/document (getElementById "everything"))}))
