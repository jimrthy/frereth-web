(ns com.frereth.web.routes.v1
  "Because the API needs to be based on revisions"
  (:require [clojure.string :as string]  ; Really just for echo's reverse
            [com.frereth.common.util :as util]
            ;; TODO: Make this go away
            [frereth-server.comms :as comms]
            [plumbing.core :as plumbing :refer (defnk)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;; TODO: Nothing except the routes actually belongs in here

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def problem-explanation
  "Something about the request was wrong"
  {:problem s/Any
   (s/optional-key :details) s/Any})

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

(defnk $echo$POST
  "Really just for initial testing"
  {:responses {200 {:reversed s/Str}
               400 problem-explanation}}
  [[:request params :- {:submit s/Str}]]
  (log/debug "echo POST handling\n" (util/pretty params))
  (if-let [s (:submit params)]
    {:reversed (string/reverse s)}
    {:status 400
     :body {:problem "Missing submit parameter"}}))
