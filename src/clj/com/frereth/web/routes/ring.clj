(ns com.frereth.web.routes.ring
  "Details about the protocol that seem generally useful"
  (:require [schema.core :as s])
  (:import [clojure.lang IPersistentMap ISeq]
           [java.io File InputStream]
           [java.security.cert X509Certificate]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def RingRequest
  "What the spec says we shall use"
  {:server-port s/Int
   :server-name s/Str
   :remote-addr s/Str
   :uri s/Str
   ;; From the Ring spec
   (s/optional-key :query-string) s/Str
   ;; This is actually added through middleware, but fnhouse
   ;; requires it
   :query-params {s/Any s/Any}
   :scheme (s/enum :http :https)
   :request-method (s/enum :get :head :options :put :post :delete)
   (s/optional-key :content-type) s/Str
   (s/optional-key :content-length) s/Int
   (s/optional-key :character-encoding) s/Str
   (s/optional-key :ssl-client-cert) X509Certificate
   :header {s/Any s/Any}
   (s/optional-key :body) InputStream})

(def LegalRingResponseBody  (s/either s/Str [s/Any] File InputStream IPersistentMap ISeq))

(s/defschema RingResponse {:status s/Int
                           :headers {s/Any s/Any}
                           (s/optional-key :body) LegalRingResponseBody})

(def HttpRequestHandler (s/=> RingResponse RingRequest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn extract-user-id :- (s/maybe s/Str)
  [ring-request :- RingRequest]
  (let [session (:session ring-request)]
    (:uid session)))
