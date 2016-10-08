(ns com.frereth.web.routes.v1
  "Because the API needs to be based on revisions"
  (:require #_[bidi.bidi :as bidi]
            [bidi.ring :refer (make-handler)]
            [clojure.spec :as s]
            [com.frereth.client.communicator :as comms]
            [com.frereth.common.communication]
            [com.frereth.common.util :as util]
            [com.frereth.web.handlers.v1 :as v1]
            [taoensso.timbre :as log]))

;;;; N.B.: Nothing except the routes actually belongs in here

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::problem any?)
(s/def ::details any?)
;; Something went wrong
;; Q: What's the proper way to specify the pieces inside a map?
;; I really want to spec that #(-> % :body) matches this and
;; pretty much leave that unchanged when I run conform on it.
;; TODO: This part needs love/experimentation too
(s/def ::problem-explanation (s/keys :req [::problem]
                                     :opt [::details]))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes
;;; TODO: Add a standard handler for the basic boiler
;;; plate.

(def dispatch
  (make-handler ["/" {"echo" v1/echo
                      "version" v1/version}]))

(def spec
  "Q: Is there any reason not to add docs here?
Note that the specs are a vital part of that.

TODO: Think this through further"
  (make-handler ["/" {"echo" {::pre #(-> % :params :submit string?)
                              ::post (s/or :illegal-input (s/and #(= (-> % :status)
                                                                     400)
                                                                 ::problem-explanation)
                                           :ok (s/and #(= (-> % :status)
                                                          200)
                                                      ::string-body))}
                      "version" {::post :com.frereth.common.communication/version}}]))
