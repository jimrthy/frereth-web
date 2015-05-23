(ns ^:figwheel-always frereth.core
    "TODO: Do something interesting here"
    (:require [schema.core :as s]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(s/defn reflect :- {s/Keyword s/Any}
  "TODO: This doesn't belong here. But it's probably a better
location than common.clj where I was storing it.
It's tempting to change that to common.cljc, but I'm not
sure there's enough cross-platform functionality to make that
worthwhile.

Return a clojurescript map version of a javascript object
It seems like js->clj should already handle this, but
I haven't had very good luck with it"
  [o]
  (let [props (.keys js/Object o)]
    (reduce (fn [acc prop]
              (assoc acc (keyword prop) (aget o prop)))
            {}
            props)))
