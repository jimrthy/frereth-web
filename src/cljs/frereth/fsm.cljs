(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [cljs.js :as cljs]
            [frereth.globals :as global]
            [om.core :as om]
            [ribol.cljs :refer [create-issue
                                *managers*
                                *optmap*
                                raise-loop]]
            [sablono.core :as sablono :include-macros true]
            [schema.core :as s :include-macros true]
            [taoensso.timbre :as log])
  (:require-macros [ribol.cljs :refer [raise]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def generic-world-description
  {:data {:type s/Keyword
          :version s/Any}
   :name global/world-id
   s/Keyword s/Any
   s/Str s/Any})

(def renderable-world-description
  {:renderer (s/=> s/Any)
   :name global/world-id})

(defmulti pre-process-body
  "Convert the supplied world description into something generically useful/usable

TODO: This really should happen in the client"
  (fn
    [world-description]
    (let [available (keys world-description)]
      (log/debug "Pre-processing new world. keys:" available))
    (let [{:keys [type version]} world-description]
      [type version])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defmethod pre-process-body :default
  [description]
  (log/error "Don't know how to deal with a world that looks like:\n"
             (pr-str description)
             "\nwith keys: "
             (keys description)))

(s/defmethod pre-process-body [:html 5] :- renderable-world-description
  [{:keys [body css script] :as html}]
  (log/debug "Switching to HTML 5")
  (raise {:not-implemented "This is more complicated/dangerous than it looks at first glance"})
  (let [renderer (fn [data owner]
                   (reify
                     om/IRender
                     (render
                      [_]
                      ;; This approach fails completely
                      (log/debug "HTML 5: simplest approach I can imagine: just return " (pr-str body))
                      body)))]
    {:name (s/conditional s/Keyword s/Str)
     :renderer renderer}))

(s/defmethod pre-process-body [:om [0 9 0]] :- renderable-world-description
  [_]
  (raise {:not-implemented "What does this even look like?"}))

;; Q: Is there an equivalent of defnk that I can use?
(s/defmethod pre-process-body [:sablono [0 3 6]] :- renderable-world-description
  [body]
  (sablono/html body))

(s/defn pre-process-script
  [name
   forms]
  (let [compiler-state (cljs/empty-state)]
    (doseq [form forms]
      (cljs/eval compiler-state
                 form
                 {;; According to the docstring, this is the only
                  ;; parameter in this map that has any effect.
                  :eval cljs/js-eval
                  :context :expr
                  :def-emits-var true
                  ;; This works for starters
                  ;; TODO: Server really should have supplied
                  ;; ns forms
                  :ns 'user
                  :source-map true
                  :verbose true}
                 (fn [{:keys [error ns value] :as res}]
                   (log/debug "Evaluating initial forms for "
                              name
                              ":\n"
                              (pr-str res)))))
    compiler-state))

(defn pre-process-styling
  [styles]
  (log/warn "TODO: Cope w/ CSS")
  styles)

(s/defn make-renderable!
  "This isn't named particularly well.

Nothing better comes to mind at the moment."
  [descr :- renderable-world-description]
  (let [data (:data descr)
        name (:name data)
        pre-processed (pre-process-body (select-keys data [:body :type :version]))
        compiler-state (pre-process-script name (:script data))
        styling (pre-process-styling (:css data))]
    (swap! global/app-state (fn [current]
                              ;; Add newly created world to the set we know about
                              ;; TODO: Seems like we might want to consider closing
                              ;; out older, unused worlds.
                              ;; That is an end-user's decision to make
                              (assoc-in current
                                        [:worlds name]
                                        (assoc (:data descr)
                                               :body (:body pre-processed)
                                               :repl {:state compiler-state}
                                               :css styling))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn transition-world!
  [to-activate]
  (log/debug "Trying to activate world: '" (pr-str to-activate) "'")
  (swap! global/app-state (fn [current]
                            (if-let [_ (-> current :worlds (get to-activate))]
                              (assoc current :active-world to-activate)
                              (raise {:unknown-world to-activate})))))

(defn initialize-world!
  "Let's get this party started!

This is really just the way the world bootstraps.

It needs to send us enough initial info to start loading the full thing.

This should happen in response to a blank-slate request
for initialization. It should never (?) happen a second time.

Once it's complete, the world should have enough information to
finish loading via update messages.

TODO: Limit the amount of time spent here
Q: Can I do that by sticking it in a go loop, trying to alts! it
with a timeout, and then somehow cancelling the transaction if it times
out?"
  [{:keys [data]
    :as description}]
  (let [{:keys [action-url
                expires
                session-token
                world]
         :as destination} data]
    (log/info "=========================================
This is important!!!!!
Initializing '"
              (pr-str (:name data))
              "' World:
=========================================\n"
              destination)
    (try
      (log/debug "Keys in new world body: " (keys destination))
      (catch js/Error ex
        (log/error "This really isn't a legal world description")))

    (when (= description :hold-please)
      (raise {:obsolete "Why don't I have the handshake toggle fixed?"}))
    (make-renderable! description))
  ;; Just binding this in keys caused issues with trying to shadow the built-in
  (let [world-name (:name data)]
    (transition-world! world-name)))
