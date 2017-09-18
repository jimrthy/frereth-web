(ns com.frereth.web.routes.core
  "Why is mapping HTTP end-points to handlers so contentious?"
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.frereth.common.util :as util]
            [com.frereth.client.communicator :as comms]
            [com.frereth.web.routes.ring :as fr-ring]
            [com.frereth.web.routes.sente :as routes-sente]
            [com.frereth.web.routes.v1 :as routes-v1]
            [com.frereth.web.routes.websock :as ws]
            ;; TODO: These all need to go away
            ;; (the trick is finding suitable replacements)
            [fnhouse.docs :as docs]
            [fnhouse.handlers :as handlers]
            [fnhouse.routes :as routes]
            #_[fnhouse.schemas :as fn-schemas]
            [hara.event :refer (raise)]
            [integrant.core :as ig]
            ;; Q: Am I getting the ring header middleware with this group?
            [ring.middleware.content-type :refer (wrap-content-type)]
            [ring.middleware.defaults]
            [ring.middleware.format :refer (wrap-restful-format)]
            ;; TODO: Look into wrap-multipart-params middleware.
            ;; Uploading files is definitely one of the major required
            ;; features.
            [ring.middleware.not-modified :refer (wrap-not-modified)]
            [ring.middleware.resource :refer (wrap-resource)]
            [ring.middleware.stacktrace :refer (wrap-stacktrace)]
            [ring.util.response :as response]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::standard-description map?)
(s/def ::standard-ctor-description (s/map-of ::http-router ::standard-description))

;; This defines the mapping between URL prefixes and the
;; namespaces where the appropriate route handlers live.
;; Copy/pasted directly from fnhouse.
;; (which means it also needs to go away)
(s/def ::path-prefix string?)
(s/def ::namespac simple-symbol?)
(s/def ::http-route-map (s/map-of ::path-prefix ::namespace))

(s/def ::frereth-client :com.frereth.client.communicator/server-socket)
(s/def ::http-router :com.frereth.web.routes.ring/http-request-handler)
(s/def ::http-routes (s/keys :opt [::frereth-client
                                   ::http-router
                                   ::ws/web-sock-handler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/fdef return-index
        :args ()
        :ret string?)
(defn return-index
  "Because there are a ton of different ways to try to access the root of a web app"
  []
  (if-let [url (io/resource "public/index.html")]
    ;; TODO: Return the stream rather than loading everything into memory here.
    ;; Then again, it isn't like "everything" is all that much.
    ;; But I've had poor experiences in the past with that resource disappearing
    ;; when we deploy the .war file.
    ;; That's something to watch out for, but, honestly, it makes more sense for
    ;; nginx to serve up the static files anyway.
    {:body (slurp url)
     :status 200
     :headers {"Content-Type" "text/html"}}
    {:body "<!DOCTYPE html>
<html>
  <head>
    <title>Oops</title>
  </head>
  <body>
    <h1>Missing Index</h1>
    <p>Odds are, there's something wrong with the way you built your .war file</p>
  </body>
</html>"
     :status 404
     :headers {"Content-Type" "text/html"}}))

;; TODO: ^:always-validate
(s/fdef index-middleware
        :args (s/cat :handler :com.frereth.web.routes.ring/http-request-handler)
        :ret :com.frereth.web.routes.ring/http-request-handler)
(defn  index-middleware
  [handler]
  (fn [req]
    (let [path (:uri req)]
      (log/debug "Requested:" path)
      (if (= path "/")
        ;; Need to add ring-anti-forgery around this
        ;; Q: Is there any real point, considering the basic
        ;; architecture I have in mind? It really isn't
        ;; for REST calls, and this is pretty much the only
        ;; "standard" HTTP request I expect to handle.
        ;; Actually, I probably shouldn't even handle this
        ;; one. The same resource handler that's getting my
        ;; cljs should be able to cope with this.
        ;; A: Sente expects/requires it.
        ;; It seems very silly to have anything in the
        ;; request chain that doesn't.
        (return-index)
        (handler req)))))

(s/fdef debug-middleware
        :args (s/cat :handler :com.frereth.web.routes.ring/http-request-handler)
        :ret :com.frereth.web.routes.ring/http-request-handler)
(defn debug-middleware
  "Log the request as it comes in.

TODO: Should probably save it so we can examine later"
  [handler]
  (fn
    [req]
    (comment (log/debug "Incoming Request:\n" (util/pretty req)))
    (handler req)))

(defn wrap-standard-middleware
  "Build the call stack of everything the standard handlers go through.

  This seems to inevitably turn into a big, hairy, nasty, tangled-up mess.

  Take the time to learn and appreciate everything that's going on here."
  [handler]
  (let [ring-defaults-config
        ;; Note that this will [probably] be sitting behind nginx in most
        ;; scenarios, so should really be using secure-site-defaults with
        ;; :proxy true (c.f. the ring-defaults README).
        ;; If only because of the current pain involved with setting up
        ;; an SSL signing chain, it really isn't feasible to just make that
        ;; the default.
        ;; (i.e. if this is a general consumer app that gets installed
        ;; everywhere, there won't be a real cert. For cases where the
        ;; server is run by someone who cares, there will).
        ;; TODO: Need a way to toggle that option
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                  ;; This is the value recommended in the sente example project.
                  ;; Note that this absolutely does work. I just spent the
                  ;; evening experimenting with that.
                  ;; The problem is absolutely my code.
                  ;; Q: where's the difference?
                  {:read-token (fn [req] (-> req :params :csrf-token))}
                  ;; This is what worked for me originally.
                  ;; According to the ring.middleware.defaults docs, this is what
                  ;; I should be using
                  ;; Q: Why does neither seem to work?
                  ;; TODO: Create a PR to reflect this?
                  #_true)]
    (-> handler
        debug-middleware  ; TODO: Only in debug mode
        ;; Q: Is there any point to this at all?
        ;;wrap-stacktrace   ; TODO: Also just in debug mode
        (ring.middleware.defaults/wrap-defaults ring-defaults-config)
        (wrap-resource "public")
        (wrap-content-type)
        (wrap-not-modified))))

(defn validator
  "This is really a gaping hole that fnhouse leaves behind.

I need to validate the incoming request and the outgoing response."
  [f]
  (fn [req]
    (let [{:keys [pre post]} (or (routes-v1/spec req)
                                 (routes-sente/spec req))
          pre-validated (if pre
                          (s/conform pre req)
                          pre)]
      (if (not= pre-validated :clojure.spec/invalid)
        (when-let [raw (f pre-validated)]
          (let [result (if post
                         (s/conform post raw)
                         raw)]
            (if (not= result :clojure.spec/invalid)
              result
              {:status 500
               :body (s/explain post raw)})))
        {:status 400
         :body (s/explain pre req)}))))

(s/fdef dispatcher
        :args (s/cat :next-handler
                     :com.frereth.web.routes.ring/http-request-handler)
        :ret :com.frereth.web.routes.ring/http-request-handler)
(defn dispatcher
  [inner]
  (fn [req]
    ;; TODO: Come up with a better approach
    (when-let [handler (or (routes-v1/dispatch req)
                           (routes-sente/dispatch req))]
      ;; TODO: Double check the order!
      (-> req inner handler))))

(s/fdef wrapped-root-handler
        :args (s/cat :component ::http-routes)
        :ret :com.frereth.web.routes.ring/http-request-handler)
(defn wrapped-root-handler
  "Returns a handler (with middleware) for 'normal' http requests

Note that this approach to middleware chaining is difficult to debug

TODO: Strongly consider breaking with tradition and running the calls
in a more imperative sequence to make it easier to debug problems
with this part."
  [component]
  (-> dispatcher
      index-middleware
      #_(wrap-sente-middleware (-> component :web-sock-handler :ch-sock))
      wrap-standard-middleware))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Components

;;; This wouldn't be worthy of any sort of existence outside
;;; the web server (until possibly it gets complex), except that
;;; the handlers are going to need access to the Connection
(defmethod ig/init-key ::http-router
  [_ {:keys [::ws/web-sock-handler]
      :as opts}]
  {:pre [web-sock-handler]}
  (assoc opts ::router (wrapped-root-handler opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public
