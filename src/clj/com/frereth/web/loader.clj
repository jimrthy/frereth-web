(ns com.frereth.web.loader
  "Feeding raw script objects to the browser

This approach made sense at first, but it simply does
not belong here. Unless I can think of some universal
library that absolutely every client should load/run,
above and on top of the core clojure namespaces.

This belongs on the server
"
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [ribol.core :refer [raise]]
            [schema.core :as s])
  (:import [java.net URL]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn find-namespace :- (s/maybe URL)
  [module-name :- s/Str
   extension-search-order :- [s/Str]]
  (let [names (string/split module-name #"\.")
        folder-names (butlast names)
        file-name (last names)
        path (str "public/js/compiled/" (string/join "/" folder-names) "/" file-name ".")]
    (when-let [almost-result (seq
                              (take 1
                                    (filter identity
                                            (map (fn [extension]
                                                   (let [actual-name (str path extension)]
                                                     (println "Searching for" actual-name)
                                                     (io/resource actual-name)))
                                                 extension-search-order))))]
      (first almost-result))))

(s/defn load-cljs-namespace :- (s/maybe s/Str)
  [module-name :- s/Str
   extension-search-order :- [s/Str]]
  (when-let [url (find-namespace module-name extension-search-order)]
    ;; Note that there really isn't any reason to assume this is a file
    (slurp (io/file url))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn load-fn-ns :- (s/maybe s/Str)
  [module-name :- s/Str]
  (raise :not-implemented {"don't do this here"})
  (load-cljs-namespace module-name ["cljs" "cljc" "js"]))

(s/defn load-macros :- (s/maybe s/Str)
  [module-name :- s/Str]
  (raise :not-implemented {"don't do this here"})
  (load-cljs-namespace module-name ["clj" "cljc"]))
