(ns frereth.repl
  "The absolte dumbest REPL implementation that I've been able to dream up"
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go]]
                   [schema.macros :as sm]
                   [schema.core :as s])
  (:require [cljs.js :as cljs]
            [cljs.core.async :refer [put! <!] :as async]
            [frereth.globals :as global]
            [om.core :as om]
            [om.dom :as dom]
            [taoensso.timbre :as log]))

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Component Handlers

(defn stripe
  [txt
   bgc]
  (let [style #js {:backgroundColor bgc}]
    (comment (println "Printing\n" txt "\n in " bgc))
    (dom/li #js {:style style} (pr-str txt))))

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
    (println "Printing eval result(s): " output " a " (type output))
    (let [table-style #js {:border "1px solid green;"}]
      (dom/tr nil
              ;; Q: How do I make this scrollable?
              (dom/td #js {:style table-style}
                      (apply dom/ul nil
                             (map stripe
                                  output (cycle ["#ff0" "#fff"])))))))
   om/IWillUnmount
   (will-unmount
    [_]
    (comment (let [evaluator (om/get-state owner :evaluated)]
               (async/close! evaluator))))))

(defn reader
  [data owner]
  (reify
    om/IInitState
    (init-state
     [_]
     {:input ""
      :namespace "user"})
    om/IRenderState
    (render-state
     [this {:keys [input namespace]}]
     (dom/div nil
      (dom/input #js {:placeholder (str namespace " =>")
                      :type "text"
                      :ref "to-read"}
                 nil)
      (dom/br nil nil)
      ;; TODO: Inline click handlers are *so* jquery
      (dom/input #js {:type "button"
                      :onClick (fn [e]
                                 (let [elm (om/get-node owner "to-read")
                                       forms (.-value elm)
                                       evaluator (om/get-state owner :evaluator)]
                                   (println "I've been clicked! Sending\n"
                                            forms "\nto\n" evaluator
                                            "which is" (if evaluator "" " not") "truthy")
                                   ;; I'm getting a warning about returning false from an event handler.
                                   ;; Q: Why?
                                   (if forms
                                     (put! evaluator forms)
                                     true)))
                      :value "Eval!"}
                 nil)))))

(defn evaluate
  [cursor forms]
  (println "Getting ready to evaluate:\n" forms)
  (let [state (get-in @global/app-state [:repls 0 :state])]
    ;; The nesting makes this more complex than needed
    ;; TODO: Really need wrap the different repls into a build-all.
    (cljs/eval-str state
                   forms
                   ;; TODO: Need to assign something meaningful
                   "Source 'File'"
                   {:context :expr  ; documented legal values are :expr, :statement, and :return
                                        ; documentation doesn't provide any hints about meaning
                    :def-emits-var true
                    :eval cljs/js-eval
                    :ns (get-in cursor [:repls 0 :namespace])
                    :source-map true
                    :verbose true}
                   (fn [{:keys [error ns value] :as res}]
                     (println "Evaluation returned:" (pr-str res))
                     (when ns
                       ;; Q: Why isn't this updating the textbox?
                       ;; I don't really care all that much, but it's annoying.
                       ;; I'm getting an assertion error that the cursor is not
                       ;; transactable
                       ;; Actually, it probably isn't the cursor at all.
                       ;; This is the data parameter that was supplied to
                       ;; repl-wrapper.
                       ;; I've probably managed to forget how Om works, again.
                       ;; Or maybe not.
                       (log/debug "Trying to update cursor, which has keys '"
                                  (-> cursor keys pr-str)
                                  "' with that result")
                       (om/transact! cursor [:repls 0 :namespace]
                                     (fn [_]
                                       (println "Latest namespace: " ns)
                                       ns)))
                     ;; TODO: If this wasn't an error, clear the text box
                     (let [response (or error value)]
                       (om/transact! cursor [:repls 0 :output]
                                     (fn [xs]
                                       (conj xs forms response))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn repl-wrapper
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
                {:evaluator (async/chan)})
    om/IWillMount
    (will-mount
     [_]
     (swap! global/app-state
            (fn [current]
              (assoc-in current [:repls 0 :state] (cljs/empty-state))))

     ;; It seems as though this is what should handle
     ;; the channel creation. Since there doesn't seem
     ;; to be a better place to close! it than IWillUnmount
     ;; But that's state that needs to be passed along
     ;; to children. So that belongs there, while this
     ;; belongs here.
     (let [evaluator (om/get-state owner :evaluator)]
       (go (loop []
             (when-let [forms (<! evaluator)]
               (evaluate data forms))
             (recur)))))

    om/IRenderState
    (render-state
     [this state]
              ;; Honestly, this is sideways.
              ;; Really want to build the result columns up,
              ;; then combine those individual components
              ;; horizontally into a table
              (let [repl (-> data :repls first)
                    heading (:heading repl)
                    output-rows (:output repl)
                    input (:input repl)
                    namespaces (:namespaces repl)
                    table-style #js {:border "1px solid black"}]
                (dom/table #js {:style table-style}
                           (dom/tbody nil
                                      ;; Header: which REPL is this?
                                      (dom/tr nil
                                              (dom/th #js {:style table-style} heading))
                                      ;; Output: what's come from the printer?
                                      (om/build printer output-rows)
                                      ;; Input: What is the user entering?
                                      (dom/tr nil
                                              (dom/td #js {:style table-style}
                                                      (om/build reader input
                                                                {:init-state state})))))))
    om/IWillUnmount
    (will-unmount
     [_]
     (comment (let [evaluator (om/get-state owner :evaluator)]
                (async/close! evaluator)))))  )
