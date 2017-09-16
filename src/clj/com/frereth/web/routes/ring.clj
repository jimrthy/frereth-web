(ns com.frereth.web.routes.ring
  "Details about the protocol that seem generally useful"
  (:require [clojure.spec.alpha :as s]
            [com.frereth.common.schema :as fr-sch]
            [com.frereth.common.zmq-socket])
  (:import [clojure.lang IPersistentMap ISeq]
           [java.io File InputStream]
           [java.security.cert X509Certificate]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema
;;; c.f. https://gist.github.com/alexanderkiel/a1b583741008aa346733a85bda074725
;;; and https://github.com/ring-clojure/ring-spec
;;; Actually, just switch to the latter

(s/def ::body (fr-sch/class-predicate InputStream))
(s/def ::character-encoding string?)
(s/def ::content-length (s/and int? (complement neg?)))
(s/def ::content-type string?)
;; Q: Do I want to spec that all the keys are going to be strings?
;; They will, won't they?
(s/def ::header map?)
;; This is actually added through middleware, but fnhouse
;; requires it
;; Q: So, do I want to mark it optional or required?
(s/def ::query-params map?)
;; This isn't very realistic
;; TODO: Get more restrictive
(s/def ::query-string string?)
(s/def ::remote-addr string?)
(s/def ::request-method #{:get :head :options :put :post :delete})
(s/def ::schema #{:http :https})
(s/def ::server-port :com.frereth.common.zmq-socket/port)
(s/def ::server-name string?)
(s/def ::ssl-client-cert (fr-sch/class-predicate X509Certificate))
(s/def ::uri string?)
;; What the spec says we shall use
(s/def ::ring-request (s/keys :req-un [::headers  ; Q: Or is this header?
                                       ::remote-addr
                                       ::request-method
                                       ::scheme
                                       ::server-name
                                       ::server-port
                                       ::uri]
                              :opt-un [::body
                                       ::character-encoding
                                       ::content-length
                                       ::content-type
                                       ::query-params
                                       ::query-string
                                       ::ssl-client-cert]))

(s/def ::legal-ring-response-body (s/or :string string?
                                        :array (s/coll-of any?)
                                        :file (fr-sch/class-predicate File)
                                        :input-stream ::body
                                        :map map?
                                        :sequence seq?))

;; Q: Which integers are truly legal here?
(s/def ::status int?)
(s/def ::ring-response (s/keys :req-un [::status
                                        ::headers]
                               ;; But the key involved here is ::body
                               ;; Q: What's the best way to spec this?
                               :opt-un [::legal-ring-response-body]))

(s/def ::http-request-handler (s/fspec :args (s/cat :req ::ring-request)
                                       :ret ::ring-response))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef extract-user-id
        :args (s/cat :req (s/cat :req ::ring-request))
        :ret (s/nilable string?))
(defn extract-user-id
  [ring-request]
  (let [session (:session ring-request)]
    (:uid session)))
