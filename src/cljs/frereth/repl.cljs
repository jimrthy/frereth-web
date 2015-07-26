(ns frereth.repl
  "The absolte dumbest REPL implementation that I've been able to dream up"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require #_[cljs.js :as compiler]
            [cljs.core.async :refer [put! <!] :as async]
            [om.core :as om]
            [om.dom :as dom]
            #_[schema.core :as s :include-macros true]))

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defonce app-state (atom {:text "Om up and running",
                          :compiler-state (comment (compiler/empty-state))
                          :repls [{:heading "Local"
                                   :output [:a :b :c]
                                   :input "=>"
                                   :namespace "user"}]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Component Handlers

(defn stripe
  [txt bgc]
  (let [style #js {:backgroundColor bgc}]
    (println "Printing\n" txt "\n in " bgc)
    (dom/li #js {:style style} (str txt))))

(defn printer
  [output owner]
  (reify
    om/IInitState
    (init-state
     [_]
     {:evaluated (async/chan)})
    om/IWillMount
    (will-mount
     [_]
     (let [evaluator (om/get-state owner :evaluated)]
       (go
         (loop []
           (when-let [evaluated (<! evaluator)]
             (om/transact! output
                           (fn [current]
                             (conj current evaluated)))
             (recur))))))
   om/IRender
   (render
    [_]
    (println "Rendering: " output " a " (type output))
    (dom/tr nil
            (dom/td #js {:style table-style}
                    (apply dom/ul nil
                           (map stripe
                                output (cycle ["#ff0" "#fff"]))))))
   om/IWillUnmount
   (will-unmount
    [_]
    (let [evaluator (om/get-state owner :evaluated)]
      (async/close! evaluator)))))

(defn reader
  [data owner]
  (reify
    om/IInitState
    (init-state
     [_]
     {:input ""
      :namespace "user"
      :evaluator (async/chan)})
    om/IWillMount
    (will-mount
     [_]
     ;; It seems as though this is what should handle
     ;; the channel creation. Since there doesn't seem
     ;; to be a better place to close! it than IWillUnmount
     (let [evaluator (om/get-state owner :evaluator)]
       (go (loop []
             (when-let [forms (<! evaluator)]
               ;; TODO: evaluate forms. Do this update inside the
               ;; callback of the evaluation instead
               (om/transact! data :output
                             (fn [xs]
                               (conj xs forms))))
             (recur)))))
    om/IWillUnmount
    (will-unmount
     [_]
     (let [evaluator (om/get-state owner :evaluator)]
       (async/close! evaluator)))
    om/IRenderState
    (render-state
     [this {:keys [input namespace]}]
     (dom/div nil
      (dom/input #js {:placeHolder (str namespace " =>")
                      :type "text"
                      :ref "to-read"}
                 nil)
      (dom/br nil nil)
      (dom/input #js {:type "button"
                      :onClick (fn [e]
                                 (let [elm (om/get-node owner "to-read")
                                       forms (.-value elm)
                                       evaluator (om/get-state owner :evaluator)]
                                   (println "I've been clicked! Sending\n"
                                            forms "\nto\n" evaluator)
                                   ;; I'm getting a warning about returning false from an event handler.
                                   ;; Q: Why?
                                   (if forms
                                     (put! evaluator forms)
                                     true)))
                      :value "Eval!"}
                 nil)))))

(defn repl-wrapper
  [data owner]
  (reify
    om/IRender
    (render
     [_]
     ;; Honestly, this is sideways.
     ;; Really want to build the result columns up,
     ;; then combine those individual components into a table
     (let [repls (:repls data)
           headings (map :heading repls)
           output-columns (mapv :output repls)
           input (map :input repls)
           namespaces (map :namespaces repls)
           table-style #js {:border "1px solid black;"}]
       (dom/table #js {:style table-style}
                  ;; Header: which REPL is this?
                  (apply dom/tr nil
                         (map (partial dom/th #js {:style table-style}) headings))
                  ;; Output: what's come from the printer?
                  (om/build-all printer output-columns)
                  ;; Input: What is the user entering?
                  (apply dom/tr nil
                         (map (partial dom/td #js {:style table-style})
                              (om/build-all reader input))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start
  []
  (om/root
   repl-wrapper
   app-state
   {:target (. js/document (getElementById "everything"))}))
