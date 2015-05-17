(ns frereth-web.system
  "This is where all the interesting stuff gets created.

It's halfway tempting to turn this into its own library,
just because I find myself copy/pasting it around a lot.

UPDATE: Great news! AvisoNovate on github has gone to
the trouble of doing that for me. Which means that
almost all of this should be able to go away.

Well, it's actually something drastically different.
aviso.config's all about merging the configuration
data from everywhere to pass into the Component
description.

Which leaves me doubting the wisdom of trying to
move the Component system itself out of here and
into its own config file.

TODO: Think that through."
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [frereth-web.db.core :as db]
            [frereth-common.util :as common]
            [io.aviso.config :as cfg]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.stuartsierra.component SystemMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

(comment
  (s/defn initialize :- [[(s/one s/Keyword "name") (s/one s/Any "instance")]]
    "require the individual namespaces and call each Component's constructor,
returning a seq of name/instance pairs that probably should have been a map

N.B. this returns key-value pairs that are suitable for passing to dependencies
as the last argument of apply"
    [descr :- {s/Keyword (s/either s/Symbol [(s/one s/Symbol "name") s/Any])}
     config-options :- {s/Any s/Any}]
    (mapcat (fn [[name ctor]]
              ;; If the config file needs parameters, it can
              ;; specify the "value" of each component as
              ;; a sequence
              (let [[ctor-sym args] (if (symbol? ctor)
                                      [ctor [{}]]  ; no args supplied
                                      [(first ctor) (rest ctor)])]
                ;; Called for the side-effects
                (-> ctor-sym namespace symbol require)
                (let [real-ctor (resolve ctor-sym)
                      instance (apply real-ctor args)]
                  [name instance])))
            descr))

  (s/defn system-map :- SystemMap
    [descr :- {s/Keyword s/Symbol}
     config-options :- {s/Any s/Any}]
    (let [inited (initialize descr config-options)]
      (apply component/system-map inited)))

  (s/defn dependencies :- SystemMap
    [inited :- SystemMap
     descr :- {s/Keyword s/Any}]
    (comment (log/debug "Preparing to build dependency tree for\n"
                        (common/pretty inited)
                        "based on the dependency description\n"
                        (common/pretty descr)))
    (component/system-using inited descr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor :- SystemMap
  "Returns a system that's ready to start, based on config.

Should mostly be pulling from EDN, which (realistically)
should be pulling everything from environment variables.

config-options are really meant to be command-line options,
which means we shouldn't have any particular use for them
in this scenario.

They're intended to be in the form of key/value pairs that
get expanded into the map."
  [& config-options]
  (comment (let [options (if (seq config-options)
                           (first config-options)
                           {})
                 descr (common/load-resource config-file-name)
                 pre-init (-> descr
                              first
                              (system-map options))]
             (dependencies pre-init (second descr))))
  (let [configuration (cfg/assemble-configuration {:prefix "frereth"
                                                   :schemas [db/URL
                                                             router/WebSocket
                                                             router/Standard
                                                             web/Server]
                                                   ;; seq of absolute file paths that will
                                                   ;; be loaded last.
                                                   ;; Typically for config files outside the classpath
                                                   :additional-files []
                                                   :args config-options
                                                   :profiles []})
        system-map (component/system-map :database (db/ctor (:database configuration))
                                         :web-socket-router (router/ctor (:web-socket configuration))
                                         :http-router (router/ctor (:http-router configuration))
                                         :web-server (web/ctor (:web-server configuration)))
        dependency-map {:web-server [:http-router :web-socket-router]
                        :http-router [:database]
                        :web-socket-router [:database]}]
    (component/system-using )))

