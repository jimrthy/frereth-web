(ns com.frereth.web.routes.sente
  "Set up handlers for promoting web sockets"
  (:require [bidi.ring :as bidi]
            [com.frereth.common.util :as util]
            [com.frereth.web.handlers.sente :as handlers]
            [com.frereth.web.routes.websock :as ws]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual routes

(def dispatch
  (bidi/make-handler ["/" {"chsk" handlers/channel-sock}]))

(def spec
  "TODO: Really should put something meaningful here"
  (bidi/make-handler ["/" {"chsk" {}}]))
