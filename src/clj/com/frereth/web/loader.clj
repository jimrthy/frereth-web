(ns com.frereth.web.loader
  "Feeding raw script objects to the browser

I'm very torn about whether this belongs here or in the client

But it *is* pretty clojurescript specific. So going with this approach for now.

Note that specialized scripts (such as anything that doesn't really belong in core)
should come from whichever server told the client to request them.

Defining 'what really belong[s] in core' is worth a lot of consideration"
  (:require [ribol.core :refer [raise]]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn load-cljs-namespace :- (s/maybe s/Str)
  [module-name :- s/Str
   extension-search-order :- [s/Str]]
  ;; At least for a dumb starter version, should be able to split
  ;; the ns by \. into folders, and then load from
  ;; resources/public/js/compiled.
  ;; That's in the classpath, right?
  (raise {:not-implemented "Get this written"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn load-fn-ns :- (s/maybe s/Str)
  [module-name :- s/Str]
  (load-cljs-namespace module-name ["cljs" "cljc" "js"]))

(s/defn load-macros :- (s/maybe s/Str)
  [module-name :- s/Str]
  (load-cljs-namespace module-name ["clj" "cljc"]))
