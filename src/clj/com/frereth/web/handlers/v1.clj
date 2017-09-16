(ns com.frereth.web.handlers.v1
  "Handler implementations for my initial API"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]  ; Really just for echo's reverse
            [ring.util.response :as res]))

;; It's tempting to set up defmethods here based on the
;; HTTP method. That would be overkill.

(defn echo
  "This is a great example of something I'm losing from fnhouse:
I no longer have a way to spec what should be in the body"
  [{:keys [params request-method] :as req}]
  (when (= request-method :post)
    (if-let [s (:submit params)]
      {:reversed (string/reverse s)}
      {:status 400
       :body {:problem "Missing submit parameter"}})))

(defn version
    "This needs to come from somewhere else automatically.
Basing it off the git commit tag would probably make a lot of sense.

TODO: See how clojurescript and core.async handle that"
  [{method :request-method}]
  (when (= method :get)
    {:headers {"Content-Type" "application/edn"}
     :body (pr-str #:com.frereth.common.communication{:major 0
                                                      :minor 1
                                                      :detail 1})}))
