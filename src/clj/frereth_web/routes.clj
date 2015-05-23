(ns frereth-web.routes
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [frereth-server.comms :as comms]
            [ribol.core :refer [raise]]
            ;; I have dependencies on several other ring wrappers.
            ;; Esp. anti-forgery, defaults, and headers.
            ;; TODO: Make use of those
            [ring.middleware.content-type :refer (wrap-content-type)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.util.response :as response]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth_server.comms Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def StandardDescription {})
(def StandardCtorDescription {:http-router StandardDescription})
(def WebSocketDescription {})

(declare return-index wrap-standard-middleware)
;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(s/defrecord HttpRoutes [frereth-server :- Connection
                         ;; This is a function that takes 1
                         ;; arg and returns a Ring response.
                         ;; TODO: Spec that schema
                         http-router]
  component/Lifecycle
  (start
   [this]
   (let [handler (fn [req]
                   (let [path (:uri req)]
                     (log/debug "Requested:" path)
                     (if (= path "/")
                       #_(response/redirect "/index.html")  ; Q: How do I really do this?
                       #_(response/resource-response "/public/index.html")
                       (return-index)
                       (response/response "Hello from Ring!"))))]
     (assoc this :http-router (wrap-standard-middleware handler))))
  (stop
   [this]
   (assoc this :handler nil)))

(def UnstartedHttpRoutes (assoc StandardDescription
                                :http-router s/Any
                                :frereth-server s/Any))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn return-index
  "Because there are a ton of different ways to try to access the root of a web app"
  []
  (let [url (io/resource "public/index.html")]
    ;; TODO: Return the stream rather than loading everything into memory here.
    ;; Then again, it isn't like "everything" is all that much.
    ;; But I've had poor experiences in the past with that resource disappearing
    ;; when we deploy the .war file.
    ;; That's something to watch out for, but, honestly, it makes more sense for
    ;; nginx to serve up the static files anyway.
    {:body (slurp url)
     :status 200
     :headers {"Content-Type" "text/html"}}))

(defn wrap-standard-middleware
  "Build the call stack of everything the standard handlers go through.

  This seems to inevitably turn into a big, hairy, nasty, tangled-up mess.

  Take the time to learn and appreciate everything that's going on here."
  [handler]
  (-> handler
      wrap-params
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ^:always-validate ctor :- UnstartedHttpRoutes
  [_ :- StandardDescription]
  (map->HttpRoutes _))
