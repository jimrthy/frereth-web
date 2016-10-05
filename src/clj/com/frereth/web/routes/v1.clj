(ns com.frereth.web.routes.v1
  "Because the API needs to be based on revisions"
  (:require [clojure.spec :as s]
            [clojure.string :as string]  ; Really just for echo's reverse
            [com.frereth.client.communicator :as comms]
            [com.frereth.common.communication]
            [com.frereth.common.util :as util]
            [taoensso.timbre :as log]))

;;;; N.B.: Nothing except the routes actually belongs in here

;; TODO: Add a standard handler for the basic boiler
;; plate.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/def ::problem any?)
(s/def ::details any?)
;; Something about the request was wrong
(s/def ::problem-explanation (s/keys :req [::problem]
                                     :opt [::details]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes

(defn $echo$POST
  "Really just for initial testing"
  [params]
  (throw (ex-info "Not Implemented" {:problem "Convert from fnhouse"}))
  {:responses {200 {:reversed string?}
               400 ::problem-explanation}}
  [[:request params :- {:submit string?}]]
  (log/debug "echo POST handling\n" (util/pretty params))
  (if-let [s (:submit params)]
    {:reversed (string/reverse s)}
    {:status 400
     :body {:problem "Missing submit parameter"}}))

(defn $version$GET
  "This needs to from somewhere else automatically.
Basing it off the git commit tag would probably make a lot of sense.

TODO: See how clojurescript and core.async handle that"
  []
  (throw (ex-info "Not Implemented" {:problem "Convert from fnhouse"}))
  {:responses {200 :com.frereth.common.communication/version}}
  ;; fnhouse ignores the headers I'm returning here.
  ;; TODO: Add middleware to fix that
  {:headers {"Content-Type" "application/edn"}
   :body (pr-str #:com.frereth.common.communication{:major 0
                                                    :minor 1
                                                    :detail 1})})
