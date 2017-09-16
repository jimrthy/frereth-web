(ns com.frereth.web.system
  "This is where all the interesting stuff gets created."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [com.frereth.common.util :as util]
            [component-dsl.system]
            [component-dsl.system :as cpt-dsl]
            [io.aviso.config :as cfg]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

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

;; TODO: What's the schema?
;; I'm using some pieces in here and ctor that are really internal
;; to component-dsl.
;; TODO: Don't do that.
(defn combine-options
  [command-line-args system-description]
  (let [ctor-schemata (-> system-description :schemas cpt-dsl/translate-schematics!)]
    (cfg/assemble-configuration {:prefix "frereth"
                                 :schemas ctor-schemata
                                 ;; seq of absolute file paths that will
                                 ;; be loaded last.
                                 ;; Typically for config files outside the classpath
                                 :additional-files []
                                 :args command-line-args
                                 :profiles []})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef ctor
        :args (s/cat :unused-command-line-args any?
                     :unused-config-file-name ::config-file-name)
        :ret :component-dsl.system/system-map)
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

  ;; Q: Do I want to go back to something more similar to this
  ;; original, commented-out version?
  ;; (i.e. where I load the EDN description from a file instead
  ;; of hard-coding it here)
  (comment
    (let [system-description (-> system-description-file-name
                                 io/resource
                                 io/reader
                                 util/pushback-reader
                                 edn/read)
          options (combine-options command-line-args system-description)
          pre-init (-> system-description
                       :initialization-map
                       (cpt-dsl/system-map options))]
      (cpt-dsl/dependencies pre-init (:dependencies system-description))))

  ;; This is where the real action starts these days
  (let [constructors '{:complete component-dsl.done-manager/ctor
                       ;; Poor name. This is really the frereth-client.
                       ;; Or maybe the frereth-server-connection.
                       ;; TODO: Either way, pick a better one.
                       :frereth-client com.frereth.client.system/init
                       :http-router com.frereth.web.routes.core/ctor
                       :web-sock-handler com.frereth.web.routes.websock/ctor
                       :web-server com.frereth.web.handler/ctor}
        dependencies  {:http-router [:frereth-client :web-sock-handler]
                       :web-server [:http-router]
                       :web-sock-handler [:frereth-client]
                       :frereth-client [:complete]}]
    (cpt-dsl/build #:component-dsl.system{:structure constructors
                                          :dependencies dependencies}
                   {})))

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
