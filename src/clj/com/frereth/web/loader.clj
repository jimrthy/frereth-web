(ns com.frereth.web.loader
  "Feeding raw script objects to the browser

I'm very torn about whether this belongs here or in the client

But it *is* pretty clojurescript specific. So going with this approach for now.

Note that specialized scripts (such as anything that doesn't really belong in core)
should come from whichever server told the client to request them.

Defining 'what really belong[s] in core' is worth a lot of consideration"
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
  (load-cljs-namespace module-name ["cljs" "cljc" "js"]))

(s/defn load-macros :- (s/maybe s/Str)
  [module-name :- s/Str]
  (load-cljs-namespace module-name ["clj" "cljc"]))
