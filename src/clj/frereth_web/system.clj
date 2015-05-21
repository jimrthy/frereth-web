(ns frereth-web.system
  "This is where all the interesting stuff gets created.

It's halfway tempting to turn this into its own library,
just because I find myself copy/pasting it around a lot.
"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [frereth-common.util :as common]
            [io.aviso.config :as cfg]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]
           [com.stuartsierra.component SystemMap]
           [java.io PushbackReader]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def NameSpace s/Symbol)
(def SchemaName s/Symbol)

(def SchemaDescription
  "Really just a map of symbols marking a namespace to
  symbols naming schemata in that namespace"
  {NameSpace (s/either SchemaName [SchemaName])})

(def Schema
  "An individual description"
  s/Any)

(def Schemata
  "Really just so I have a meaningful name to call these things"
  [Schema])

(def ComponentName s/Keyword)
(def ComponentInitializer (s/either s/Symbol [(s/one s/Symbol "name") s/Any]))
(def InitializationMap {ComponentName ComponentInitializer})

(def ComponentInstanceName
  "TODO: Try redefining ComponentName as this
I'm just not sure that'll work in non-sequences
(such as when I'm using it as the key in a map)"
  (s/one s/Keyword "name"))
(def ComponentInstance (s/one s/Any "instance"))
(def Component [ComponentInstanceName ComponentInstance])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

(s/defn load-var :- s/Any
  "Get the value of var inside namespace"
  [namespace :- s/Symbol
   var-name :- s/Symbol]
  (let [sym (symbol (str namespace "/" var-name))]
    (try
      (eval sym)
      (catch RuntimeException ex
        ;; Logger isn't initialized yet
        (print "Loading" var-name "from" namespace "failed")
        (raise {:problem var-name
                :reason ex})))))

(s/defn require-schematic-namespaces!
  "Makes sure all the namespaces where the schemata
are found are available, so we can access the schemata"
  [d :- SchemaDescription]
  (dorun (map require (keys d))))

(comment
  (mapcat (fn [[k v]]
            (println "Extracting" v "from ns" k)
            (if (symbol? v)
              (load-var k v)
              (mapcat (partial load-var k) v)))
          {'one '[schema-a schema-b]
           'two 'schema-a}))

(s/defn extract-schema :- Schemata
  "Returns a seq of the values of the vars in each namespace"
  [d :- SchemaDescription]
  (mapcat (fn [[k v]]
         (if (symbol? v)
           [(load-var k v)]
           (map (partial load-var k) v)))
       d))

(s/defn translate-schematics! :- Schemata
  "require the namespace and load the schema specified in each.

N.B. Doesn't even think about trying to be java friendly. No defrecord!"
  [d :- SchemaDescription]
  (require-schematic-namespaces! d)
  (extract-schema d))

;;; This actually returns a sequence of the pairs that form a Component.
;;; So something like (concat [ComponentInstanceName ComponentInstance] ...)
;;; Q: How can I specify that?
;;; Q: Would it be better to use map instead of mapcat to build this
;;; sequence, then concat it before I actually use it?
(s/defn ^:always-validate initialize! :- [Component]
  "require the individual namespaces and call each Component's constructor,
returning a seq of name/instance pairs that probably should have been a map

N.B. this returns key-value pairs that are suitable for passing to dependencies
as the last argument of apply"
  [descr :- InitializationMap
   config-options :- {s/Any s/Any}]
  (mapcat (fn [[name ctor]]
            ;; Called for the side-effects
            ;; This should have been taken care of below,
            ;; in the call to require-schematic-namespaces!
            ;; But better safe than sorry
            (-> ctor namespace symbol require)
              
            (let [real-ctor (resolve ctor)
                  ;; Note the way this couples the Component name
                  ;; w/ the options.
                  ;; I'm not sure that's a bad thing
                  instance (real-ctor (name config-options))]
              [name instance]))
          descr))

(s/defn system-map! :- SystemMap
  [descr :- InitializationMap
   config-options :- {s/Any s/Any}]
  (let [inited (initialize! descr config-options)]
    (apply component/system-map inited)))

(s/defn dependencies :- SystemMap
  [inited :- SystemMap
   descr :- {s/Keyword s/Any}]
  (comment (log/debug "Preparing to build dependency tree for\n"
                      (common/pretty inited)
                      "based on the dependency description\n"
                      (common/pretty descr)))
  (component/system-using inited descr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

;; TODO: What's the schema?
(defn combine-options
  [command-line-args system-description]
  (let [ctor-schemata (-> system-description :schemas translate-schematics!)]
    (cfg/assemble-configuration {:prefix "frereth"
                                 :schemas ctor-schemata
                                 ;; seq of absolute file paths that will
                                 ;; be loaded last.
                                 ;; Typically for config files outside the classpath
                                 :additional-files []
                                 :args command-line-args
                                 :profiles []})))

(s/defn pushback-reader :- PushbackReader
  "Probably belongs under something like utils.
Yes, it does seem pretty stupid"
  [reader]
  (PushbackReader. reader))

(s/defn ctor :- SystemMap
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
  [command-line-args
   system-description-file-name :- s/Str]
  ;; TODO: Pull these out of the SystemMap description
  (let [system-description (-> system-description-file-name
                               io/resource
                               io/reader
                               ;; It seems fucking ridiculous that it's so
                               ;; complicated to build this stupid thing
                               pushback-reader
                               edn/read)
        options (combine-options command-line-args system-description)
        pre-init (-> system-description
                     :initialization-map
                     (system-map! options))]
    (dependencies pre-init (:dependencies system-description))
    (component/system-using dependencies)))

;; TODO: This needs to be a unit test
(comment
  (let [command-line-args []
        system-description-file-name "frereth.system.edn"
        system-description (-> system-description-file-name
                               io/resource
                               io/reader
                               ;; It seems fucking ridiculous that it's so
                               ;; complicated to build this stupid thing
                               pushback-reader
                               edn/read)
        options (combine-options command-line-args system-description)
        init-map (:initialization-map system-description)]
    (system-map! init-map options)))

;; TODO: Also needs to be a unit test
(comment (let [command-line-args []
               system-description-file-name "frereth.system.edn"
               system-description (-> system-description-file-name
                                      io/resource
                                      io/reader
                                      ;; It seems fucking ridiculous that it's so
                                      ;; complicated to build this stupid thing
                                      pushback-reader
                                      edn/read)
               options (combine-options command-line-args system-description)
               init-map (:initialization-map system-description)]
           (initialize! init-map options))

         )


