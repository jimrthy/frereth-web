(ns frereth.dispatcher
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                   [ribol.cljs :refer [raise]])
  (:require [cljs.core.async :as async]
            [frereth.fsm :as fsm]
            [frereth.globals :as global]
            [frereth.world :as world]
            [ribol.cljs :refer [create-issue
                                *managers*
                                *optmap*
                                raise-loop]]
            [schema.core :as s :include-macros true]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def return-codes
  "It's very tempting to just return a ring response here"
  {:status schema.core/Int
   :body s/Any
   :request s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Named "Constants"

(def event-response-timeout-ms 5000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn not-found
  [request]
  {:status 404
   :body "\"Not Found\""
   :request (dissoc request :ch-recv :send-fn)})

(defn standard-cb-notification
  [sent-message
   cb-reply]
  (log/debug "Received\n" cb-reply
             "\nin response to:\n" sent-message))

(defn send-standard-event
  "Q: How realistic is this approach?
It works for basic request/response pairs, but fails in the
general case of ordinary event dispatching.

It almost seems like it would be better to just write everything
from the 'ordinary event dispatching' stand-point to keep the entire
approach unified"
  [send-fn
   event-type
   event-data]
  (let [event [event-type event-data]]
    (send-fn event event-response-timeout-ms (partial standard-cb-notification event))))

(defn real-dispatcher
  [event]
  (log/debug "real-dispatcher handling:\n" event)
  (let [[event-type
         body] event]
    (log/debug ":chsk/recv around a " event-type
               "\nMessage Body:\n" body)
    (condp = event-type
      :frereth/start-world (fsm/start-world! body true)
      ;; I'm getting this and don't know why.
      ;; TODO: Track it down
      :http/not-found (log/error "Server Did Not Find:\n" body)
      :else (log/error "Unhandled event-type: " event-type
                       "\nMessage Body:\n" (pr-str body)))))

(s/defn send-blank-slate!
  "Have client notify a server that we want to learn about its world(s)"
  [send-fn
   world-id :- world/template
   world-url :- global/world-url]
  (js/alert "Sending blank-slate request for " world-id "at" world-url)
  (send-standard-event send-fn :frereth/blank-slate {:url world-url
                                                     :request-id world-id}))

(defn connect-to-initial-world!
  [send-fn]
  ;; It seems more than a little wrong to just arbitrarily update
  ;; the currently active world state.
  ;; What if the connection drops then reloads?
  ;; TODO: This really should be all about the "localhost"
  ;; default connection. Which, really, is pretty special.
  (if-let [active-world (global/get-active-world)]
    (do
      ;; Actually, we should already have switched, due to a dropped connection event
      ;; This should actually let us switch back to the world we care about
      ;; Although there are probably plenty of worlds that couldn't care less about
      ;; the connection.
      ;; Like, say, one for playing solitaire.
      (raise {:not-implemented "Really need to switch back to initial world and indicate dropped connection"})
      (global/swap-world-state! active-world (constantly :re-connecting)))
    ;; Set up the "real" initial world
    (let [localhost {:protocol :tcp
                     :address "127.0.0.1"
                     :port 7848
                     :path "get-login"}
          response-chan (fsm/initialize-world! localhost)]
      (log/debug "Requesting a fresh world connection")
      (go
        (if-let [world-template (async/<! response-chan)]
          (do
            (log/info (str "Initialized empty, unknown world: " (:world-id world-template)))
            (global/add-world! world-template )
            (send-blank-slate! send-fn world-template localhost))
          (log/error "Failed to initialize a new world at\n"
                     (pr-str localhost)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn handle!
  [{:keys [send-fn event id ?data state] :as message-batch}]
  (comment) (log/debug "Incoming message-batch:\n"
                       (keys message-batch)
                       ;; It seems as though logging state here would be
                       ;; extremely helpful.
                       ;; It isn't.
                       ;; And, honestly, it's a black box that I probably
                       ;; shouldn't ever know anything about.
                       "\nEvent: " event
                       "\nID: " id
                       "\nData: " ?data)

  ;; This is a cheese-ball dispatching mechanism, but
  ;; anything more involved is YAGNI
  (condp = id
    :chsk/handshake (do (log/info "Initial Channel Socket Handshake received")
                        ;; Note that this is a go block. We don't care about
                        ;; its return value, but silently discarding it seems
                        ;; like a bad idea
                        (connect-to-initial-world! send-fn))
    :chsk/recv (real-dispatcher ?data)
    :chsk/state (log/info "ChannelSock State message received:\n"
                          ?data)
    ;; Letting the server just take control here would be a horrible idea
    :frereth/initialize-world (do
                                (log/info "Switching to new world")
                                (raise {:obsolete "This really comes in as a :chsk/recv message"})
                                (fsm/transition-to-world! ?data))
    :frereth/response (do
                        (log/debug "Request response received:\n"
                                   "I should try to do something intelligent with this,\n"
                                   "but it should really be handled in its own callback")
                        (raise {:not-expected "This should come in as the data body of a :chsk/recv"}))
    :else (let [cleaned-request (dissoc message-batch :ch-recv :send-fn :state)
                response (not-found cleaned-request)]
            (log/warn "Don't have a handler for:\n" cleaned-request)
            (send-standard-event send-fn :frereth/response response))))
