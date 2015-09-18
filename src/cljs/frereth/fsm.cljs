(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [cljs.core.async :as async]
            [cljs.js :as cljs]
            [cljs-uuid-utils.core :as uuid]
            [frereth.globals :as global]
            [om.core :as om]
            [ribol.cljs :refer [create-issue
                                *managers*
                                *optmap*
                                raise-loop]]
            [sablono.core :as sablono :include-macros true]
            [schema.core :as s :include-macros true]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                   [ribol.cljs :refer [raise]])
  (:import [goog.net XhrIo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def compiler-black-box
  "Doesn't really belong in here, but it'll do as a start"
  s/Any)

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

(defn bootstrap-loader
  "How the initial bootstrapper can locate the namespace(s) it must have.
TODO: Worlds really need a way to specify their own loader, once they've been bootstrapped."
  [{:keys [name macros path] :as libspec}
   cb]
  (log/debug "Trying to load" name "at" path)
  (comment
    ;; This is what the server needs to do
    ;; TODO: Actually load the requested library
    ;; If macros is true, search for .clj, then .cljc
    ;; Otherwise, search for .cljs, cljc, and .js (in order)
    (let [language :clj  ; or :js
          source (raise {:not-implemented "Actual source for the requested lib"})
          ;; If :clj has been precompiled to :js, can give an analysis cache for faster loads
          cache nil
          ;; Will almost always want something here
          source-map nil]
      (let [result {:lang lang
                    :source source
                    :cache cache
                    :source-map source-map}]
        (cb result))))
  (let [request {:module-name name, :macro? macros :path path}]
    ;; Really have to specify the destination server
    (raise {:not-implemented "Write the rest of this"})))

(s/defn pre-process-script
  [name
   forms]
  (let [compiler-state (raise {:not-implemented "Pull from init'd world"})]
    (doseq [form forms]
      (cljs/eval compiler-state
                 form
                 {:eval cljs/js-eval
                  :load loader}
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

(defn get-file
  "Copied straight from swannodette's cljs-bootstrap sample"
  [url]
  (raise :not-implemented "TODO: Load this over sente instead")
  (let [c (async/chan)]
    (.send XhrIo url
           (fn [e]
             (comment
               (log/info "Server response re: "
                         url
                         "\n"
                         (pr-str e)
                         "\naka\n"
                         ;; Object is not ISeqable
                         #_(mapcat (fn [[k v]]
                                     (str "\n" k " : " v))
                                   e)
                         #_(pr-str (js->clj e :keywordize-keys true)))
               ;; This is really what I was looking for
               ;; TODO: Turn this into a utility function, if only so I
               ;; don't have to keep digging around for it
               (.dir js/console e))
             (let [target (.-target e)
                   outcome {(if (.isSuccess target)
                              :success
                              :fail)
                            (.getResponseText target)}]
               (async/put! c outcome))))
    c))

(defn initialize-compiler
  "Set up a compilation environment that's ready to be useful

TODO: Desperately needs to be memoized

This feels more than a little heavy-handed. Surely there are environments which
won't need/want this full treatment.
"
  [outcome-chan]
  (log/debug "Initializing compiler")
  ;; Note that empty-state accepts an init function
  ;; Q: What's that for?
  (cljs/empty-state)
  (comment
    ;; This can all go away, as soon as I have the "real" evaluator
    ;; using a loader that points to the appropriate server
    (let [st (cljs/empty-state)
          _ raise {:obsolete "Do everything else from server"}
          namespace-declaration ']
      (cljs/eval st
                 namespace-declaration
                 {:eval cljs/js-eval
                  :load bootstrap-loader}
                 (fn [{:keys [error value]}]
                   (if error
                     (do
                       (log/error error)
                       ;; Take the sledge-hammer approach
                       (async/close! outcome-chan))
                     (do
                       (log/info "Loaded successfully! (and there was much rejoicing)")
                       (async/put! outcome-chan st))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public
;;;
;;; N.B. Start w/ initialize-world! as a blank default
;;; When we have a description, call start-world! on it
;;; Use transition-to-world! to make a given world active

(s/defn initialize-world! :- s/Str
  "TODO: Really need a way to load multiple views of the same world instance"
  []
  {:world-id (uuid/make-random-uuid)
   :compiler-state (initialize-compiler)})

(defn transition-to-world!
  [to-activate]
  (log/debug "Trying to activate world: '" (pr-str to-activate) "'")
  (swap! global/app-state (fn [current]
                            (if-let [_ (-> current :worlds (get to-activate))]
                              (assoc current :active-world to-activate)
                              (raise {:unknown-world to-activate})))))

(s/defn start-world!
  "Let's get this party started!

Server has returned bootstrap info.

This is really just the way the world bootstraps.

It seems like this conflicts w/ transition-world!, but it really doesn't.

It needs to send us enough initial info to start loading the full thing.

This should happen in response to a blank-slate request
for initialization. It should never (?) happen a second time.

Once it's complete, the world should have enough information to
finish loading via update messages.

TODO: Limit the amount of time spent here
Q: Can I do that by sticking it in a go loop, trying to alts! it
with a timeout, and then somehow cancelling the transaction if it times
out?"
  ([{:keys [data]
     :as description}
    transition :- s/Bool]
   (let [{:keys [action-url
                 expires
                 request-id
                 session-token
                 world]
          :as destination} data]
     (raise {:not-implemented "Already have world initialized"})
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
     (make-renderable! description)
     ;; It's very tempting to call transition-to-world! as the next step
     ;; But, really, that's up to the caller
     (when transition
       (transition-to-world! request-id))))
  ([data]
   (start-world! data false)))
