(ns frereth.fsm
  "This is a misnomer. But I have to start somewhere."
  (:require [cljs.core.async :as async]
            [cljs.js :as cljs]
            [cljs-uuid-utils.core :as uuid]
            [frereth.globals :as global]
            [frereth.schema :as fr-skm]
            [frereth.world :as world]
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
  [send-fn
   world-id
   {:keys [name macros path] :as libspec}
   cb]
  (let [msg (str "Trying to load '" name "' at '" path "' with macros: " macros
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
  (let [request {:module-name name, :macro? macros :path path :world world-id}]
    ;; TODO: Need a request/response exchange w/ the server
    (send-fn request)
    (cb nil)))

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
    ;; This updates the compiler-state atom in place
    ;; Q: Doesn't it?
    (doseq [form forms]
      (log/debug "Eval'ing:\n" (pr-str form))
      (cljs/eval compiler-state
                 form
                 {:eval cljs/js-eval
                  :load loader}
                 (fn [{:keys [error ns value] :as res}]
                   (log/debug "Evaluating initial forms for "
                              name
                              ":\nError:" (pr-str error)
                              "\nSuccess Value:" (pr-str value)
                              "\nns:" (pr-str ns)))))
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
  [descr :- renderable-world-description]
  (log/debug "Have a description to make renderable.\nKeys available:\n" (keys descr))
  (let [data (:data descr)
        name (:name data)
        pre-processed (pre-process-body (select-keys data [:body :type :version]))
        world-id (:request-id descr)
        _ (pre-process-script! world-id loader name (:script data))
        styling (pre-process-styling (:css data))]
    ;; This really shouldn't happen here.
    ;; TOOD: Move it into globals ns
    ;; Q: What about compiler-state?
    (log/debug "Everything processed. Updating world state")
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
    (log/debug "Global app-state updated")))

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

This feels more than a little silly, since it's just a wrapper
around one function call.

Until/unless I memoize it.
"
  []
  (log/debug "Initializing compiler")
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

(defn transition-to-world!
  [to-activate]
  (raise {:obsolete "Just call global/set-active-world! instead"}))

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
  ([{:keys [data]
     :as description}
    transition :- s/Bool]
   ;; Just to make it easier to track what I'm seeing during debugging
   (reset! most-recent-world-description description)

   (let [{:keys [action-url
                 expires
                 request-id
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
               (pr-str destination))
     (try
       (log/debug "Keys in new world body: " (keys destination))
       (catch js/Error ex
         (log/error "This really isn't a legal world description")))

     (when (= description :hold-please)
       (raise {:obsolete "Why don't I have the handshake toggle fixed?"}))

     (js/alert "Making new world renderable")
     (make-renderable! description)
     ;; It's very tempting to call set-active-world! as the next step
     ;; But, really, that's up to the caller

     (when transition
       (global/set-active-world! request-id))))
  ([data]
   (start-world! data false)))
