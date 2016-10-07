(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [cljs.core.async :as async]
            [cljs.js :as cljs]
            [cljs-uuid-utils.core :as uuid]
            [frereth.globals :as global]
            [frereth.schema :as fr-skm]
            [frereth.world :as world]
            [om.core :as om]
            [sablono.core :as sablono :include-macros true]
            [schema.core :as s :include-macros true]
            [taoensso.sente :as sente]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:import [goog.net XhrIo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def library-spec
  {:name s/Str
   :macros s/Bool
   :path s/Str})

(def generic-world-description
  {:data {:type s/Keyword
          :version s/Any}
   :name fr-skm/world-id
   s/Keyword s/Any
   s/Str s/Any})

(def renderable-world-description
  {:renderer (s/=> s/Any)
   :request-id fr-skm/world-id})

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

(def +loader-timeout+
  "How long do we wait for a server to respond to a load-ns request?"
  2000)

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

(defn loader
  "Pull (probably pre-compiled) source from the server"
  [world-id
   send-fn
   {:keys [name macros path] :as libspec}
   cb]
  (let [msg (str "Trying to load '" name "' at '" path "' with macros: " (pr-str macros)
             "\nAKA:\n'" libspec
             "'\nworld-id:" world-id)]
    (log/debug msg))
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
  (let [?ev-data {:module-name name, :macro? macros :path path :world world-id :request-id (uuid/make-random-uuid)}
        internal-cb (fn [reply]
                      (log/debug "Response to ns load request:\n" (pr-str reply))
                      (if (sente/cb-success? reply)
                        (do
                          (log/debug "Client returned a legit response")
                          (let [[ev-id data] reply
                                status (:status data)]
                            (if (= status 200)
                              (do
                                (log/debug "Have a ns to load")
                                (cb (:body data)))
                              (do
                                (log/error "...but it wasn't a ns we can load")
                                (cb nil)))))
                        (do
                          (log/debug "ns load response was a communications failure")
                          (cb nil))))]
    (send-fn [:cljs/load-ns ?ev-data] +loader-timeout+ internal-cb)))

(s/defn pre-process-script!
  "Q: Isn't this really just 'process script'?
We very well might get updated functions to run as the world changes"
  [world-key :- fr-skm/world-id
   loader :- (s/=> s/Any library-spec)
   name :- s/Str
   forms   ; Q: What's a good schema for this? :- [[]]
   ]
  (log/debug "Processing script for the" (pr-str world-key) "compiler")
  (if-let [compiler-state (global/get-compiler-state world-key)]
    (let [waiter (async/chan)]
      (go-loop [form (first forms)
                remainder (rest forms)]
        (log/debug "Eval'ing:\n" (pr-str form))
        ;; This updates the compiler-state atom in place
        ;; Q: Doesn't it?
        (cljs/eval compiler-state
                   form
                   {:eval cljs/js-eval
                    :load loader}
                   (fn [{:keys [error ns value] :as res}]
                     (try
                       (log/debug "Evaluating initial forms for "
                                  name
                                  ":\nError:" (pr-str error)
                                  "\nSuccess Value:" (pr-str value)
                                  "\nns:" (pr-str ns))
                       (if value
                         (async/>! waiter value)
                         (async/close! waiter))
                       (catch js/Error ex
                         (log/error ex "Trying to log eval outcome")
                         (async/close! waiter)))))
        (let [[success channel] (async/alts! [waiter (async/timeout +loader-timeout+)])]
          (if-not success
            (let [msg (str "Evaluation didn't succeed:\n"
                           (if (= channel waiter)
                             "Evaluator signalled failure"
                             "Timed out"))]
              (log/error msg)
              (raise :eval-failure))
            (when (seq remainder)
              (recur (first remainder) (rest remainder)))))))
    (do
      (log/error "No compiler state for pre-processing script at" world-key "!!")
      (raise :what-to-do?))))

(defn pre-process-styling
  [styles]
  (log/warn "TODO: Cope w/ CSS")
  styles)

(s/defn make-renderable!
  "This isn't named particularly well.

Nothing better comes to mind at the moment."
  [descr :- renderable-world-description
   send-fn]
  (log/debug "Have a description to make renderable.\nKeys available:\n" (keys descr))
  (let [data (:data descr)
        name (:name data)
        pre-processed (pre-process-body (select-keys data [:body :type :version]))
        world-id (:request-id descr)
        script-loader (partial loader world-id send-fn)
        eval-go-block (pre-process-script! world-id script-loader name (:script data))
        styling (pre-process-styling (:css data))]
    ;; This really shouldn't happen here.
    ;; TOOD: Move it into globals ns
    ;; Q: What about compiler-state?
    (log/debug "Body and CSS processed. Script handling queued. Updating world state")
    (swap! global/app-state (fn [current]
                              ;; Add newly created world to the set we know about
                              ;; TODO: Seems like we might want to consider closing
                              ;; out older, unused worlds.
                              ;; That is an end-user's decision to make
                              (assoc-in current
                                        [:worlds name]
                                        (assoc (:data descr)
                                               :body (:body pre-processed)
                                               :css styling))))
    (log/debug "Global app-state updated")
    eval-go-block))

(defn initialize-compiler
  "Set up a compilation environment that's ready to be useful

TODO: Desperately needs to be memoized

This feels more than a little silly, since it's just a wrapper
around one function call.

Until/unless I memoize it.
"
  []
  ;; Note that empty-state accepts an init function
  ;; Q: What's that for?
  (cljs/empty-state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public
;;;
;;; N.B. Start w/ initialize-world! as a blank default
;;; When we have a description, call start-world! on it
;;; Use global/set-active-world! to make a given world active

(s/defn initialize-world! :- fr-skm/world-template
  "TODO: Really need a way to load multiple views of the same world instance"
  [url :- fr-skm/world-id]
  (try
    {:world-id (uuid/make-random-uuid)
     :compiler-state (initialize-compiler)
     :url url}
    (catch js/Error ex
      (log/error ex "Initializing a new empty world failed!"))))

;; To aid in debugging. What did the server send me last?
(defonce most-recent-world-description
  (atom nil))

(s/defn start-world!
  "Let's get this party started!

Server has returned bootstrap info.

This is really just the way the world bootstraps.

It seems like this conflicts w/ global/set-active-world!, but it really doesn't.

It needs to send us enough initial info to start loading the full thing.

This should happen in response to a blank-slate request
for initialization. It should never (?) happen a second time.

Once it's complete, the world should have enough information to
finish loading via update messages.

TODO: Limit the amount of time spent here
Q: Can I do that by sticking it in a go loop, trying to alts! it
with a timeout, and then somehow cancelling the transaction if it times
out?"
  ([{:keys [data request-id]
     :as description}
    send-fn
    transition :- s/Bool]
   ;; Just to make it easier to track what I'm seeing during debugging
   (reset! most-recent-world-description description)

   (let [{:keys [expires
                 session-token
                 world]
          :as destination} data]
     (log/info "=========================================
This is important!!!!!
Initializing '"
               ;; using :name screws up destructuring because of the conflict
               ;; w/ the built-in
               ;; TODO: Come up with something better
               (pr-str (:name data))
               "' World:"
               (pr-str request-id)
               "=========================================\n"
               (pr-str description))
     (try
       (log/debug "Keys in new world body: " (keys destination)
                  "\nin data:" (keys data)
                  "\nin description:" (keys description))
       (catch js/Error ex
         (log/error "This really isn't a legal world description")))

     (when (= description :hold-please)
       (raise {:obsolete "Why don't I have the handshake toggle fixed?"}))

     (js/alert "Making new world renderable")
     (let [eval-go-block (make-renderable! description send-fn)]
       (go
         (if-let [success (async/<! eval-go-block)]
           (do
             (log/info "Initial evaluation completed successfully:\n"
                       (pr-str success)
                       "\nTransitioning to that world")
             (when transition
               (global/set-active-world! request-id)))
           (do
             (log/info "Initial evaluation failed")
             ;; TODO: Error handling
             (raise {:not-implemented "Start by just deleting that world"})))))))
  ([data send-fn]
   (start-world! data send-fn false)))
