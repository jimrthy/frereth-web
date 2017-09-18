(ns com.frereth.web.system
  "This is where all the interesting stuff gets created."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.frereth.client.system :as client]
            [com.frereth.common.util :as util]
            [com.frereth.web.handler :as web-handler]
            [com.frereth.web.routes.core :as web-routes]
            [com.frereth.web.routes.websock :as ws-routes]
            [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::complete #(instance? (class (promise) %)))

(s/def ::system (s/keys :req [::client/system
                              ::complete
                              ::web-routes/http-router
                              ::web-handler/server
                              ::ws-routes/handler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

(defn configure-logging!
  "Doesn't belong here
TODO: Move to a Component in common"
  [log-file-name]
  (log/set-config!
   ;; TODO: This needs to rotate
   [:appenders :spit :enabled?] true)
  (log/set-config! [:shared-appender-config :spit-filename]
                   (str (util/pick-home) "/" log-file-name ".log"))
  (log/info "Logging configured"))

(defmethod ig/init-key ::complete
  [_ _]
  ;; Q: Am I ever actually using this for anything?
  {::finished (promise)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef ctor
        :args (s/cat :unused-command-line-args any?
                     :unused-config-file-name ::config-file-name)
        :ret ::system)
(defn ctor
  "Returns a system that's ready to start, based on config
  files.

  Should mostly be pulling from EDN, which (realistically)
  should be pulling everything from environment variables.

  command-line-args is really meant to be a seq of
  command-line options,
  which means we shouldn't have any particular use for them
  in this scenario.

  They're intended to be in the form of key/value pairs that
  get expanded into the map according to some rules on which
  I'm not yet clear.

  According to the unit tests:
  foo/bar=baz => {:foo {:bar \"baz\"}}
  Merging {:foo {:bar \"baz\"}} with foo/gnip=gnop
    => {:foo {:bar \"baz\", :gnip \"gnop\"}}

  extra-files: seq of absolute file paths to merge in. For
  the sake of setting up configuration outside the CLASSPATH
"
  [command-line-args config-file-name]

  ;; This *will* fail at runtime.
  ;; I need to sort out a new technique for pulling
  ;; options out of the environment, almost immediately.
  ;; Actually, pretty much as soon as I can get this to
  ;; compile.
  (let [options (merge "this can't work" command-line-args config-file-name)]
    ;; Q: Do I want to go back to something more similar to this
    ;; original, commented-out version?
    ;; (i.e. where I load the EDN description from a file instead
    ;; of hard-coding it here)
    {::client/system  (merge {::complete (ig/ref ::complete)}
                             (get options ::client-system {}))
     ::complete {}
     ::web-routes/http-router (merge {::web-routes/client-system (ig/ref ::client/system)
                                      ::web-routes/web-sock-handler (ig/ref ::ws-routes/web-sock-handler)}
                                     (get options ::http-router {}))
     ::web-handler/server (merge {::web-handler/http-router (ig/ref ::web-routes/http-router)}
                                 (get options ::web-server {}))
     ::ws-routes/handler (merge {::ws-routes/client-system (ig/ref ::client/system)}
                                (get options ::ws-handler {}))}))

;; TODO: This needs to be a unit test
(comment
  (let [command-line-args []
        system-description-file-name "frereth.system.edn"
        system-description (-> system-description-file-name
                               io/resource
                               io/reader
                               util/pushback-reader
                               edn/read)
        options (combine-options command-line-args system-description)
        descr (:initialization-map system-description)
        inited-pairs (initialize! descr options)
        inited (apply concat inited-pairs)]
    (let [sys-map (apply component/system-map inited)]
      inited
      (keys sys-map))))

;; TODO: Also needs to be a unit test
(comment (let [command-line-args []
               system-description-file-name "frereth.system.edn"
               system-description (-> system-description-file-name
                                      io/resource
                                      io/reader
                                      ;; It seems ridiculous that it's so
                                      ;; complicated to build this stupid thing
                                      util/pushback-reader
                                      edn/read)
               options (combine-options command-line-args system-description)
               init-map (:initialization-map system-description)]
           init-map))
