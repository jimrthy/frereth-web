(ns frereth-web.routes.v1
  "Because the API needs to be based on revisions"
  (:require [frereth-server.comms :as comms]
            [plumbing.core :as plumbing :refer (defnk)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;; TODO: Nothing except the routes actually belongs in here

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def version {:major s/Int
              :minor s/Int
              :build s/Int})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal helpers

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes

(defnk $version$GET
  "This needs to from somewhere else automatically.
Basing it off the git commit tag would probably make a lot of sense.

TODO: See how clojurescript and core.async handle that"
  {:responses {200 version}}
  []
  ;; fnhouse ignores the headers I'm returning here.
  ;; TODO: Add middleware to fix that
  {:headers {"Content-Type" "application/edn"}
   :body (pr-str {:major 0
                  :minor 1
                  :build 1})})
