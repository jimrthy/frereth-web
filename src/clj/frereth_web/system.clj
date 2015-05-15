(ns frereth-web.system
  "This is where all the interesting stuff gets created.
It's halfway tempting to turn this into its own library,
just because I find myself copy/pasting it around a lot"
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [frereth-common.util :as common]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.stuartsierra.component SystemMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

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
  (component/system-using inited descr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  "config-file-name needs to be EDN describing a seq of maps.

I go back and forth about whether it's better to supply the file name
here or make the caller responsible for extracting that.

TODO: Move the file extraction into its own function. Unit tests
can bypass that so I don't have to clutter everything up with
test files.

The first is a map of component identifiers to their constructors.
The second describes the dependencies among components"
  [config-file-name & config-options]
  (let [options (if (seq config-options)
                  (first config-options)
                  {})
        descr (common/load-resource config-file-name)
        pre-init (-> descr
                     first
                     (system-map options))]
    (dependencies pre-init (second descr))))

