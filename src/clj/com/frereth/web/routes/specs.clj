(ns com.frereth.web.routes.specs
  (:require [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs
;;; TODO: Pick somewhere te move this

(s/def ::problem any?)
(s/def ::details any?)
;; Something went wrong
;; Q: What's the proper way to specify the pieces inside a map?
;; I really want to spec that #(-> % :body) matches this and
;; pretty much leave that unchanged when I run conform on it.
;; TODO: This part needs love/experimentation too
;; Maybe I can just use s/merge?
(s/def ::problem-explanation (s/keys :req [::problem]
                                     :opt [::details]))
