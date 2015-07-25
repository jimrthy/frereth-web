(ns frereth.repl
  (:require #_[cljs.js]
            [om.core :as om]
            [om.dom :as dom]))

;;;; The absolte dumbest REPL implementation that comes to mind

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defonce app-state (atom {:text "Om up and running"}))

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
   {:target (. js/document (getElementyId "everything"))}))
