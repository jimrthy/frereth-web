(ns frereth.rendering
  "This is almost definitely its own folder.

It's probably the biggest namespace in this entire project.

But I'll start modestly, under the YAGNI theory of design."
  (:require [frereth.globals :as global]
            [om.core :as om]
            [ribol.cljs :refer (create-issue
                                *managers*
                                *optmap*
                                raise-loop)])
  (:require-macros [ribol.cljs :refer (raise)]
                   [schema.core :as s]
                   [schema.macros :as sm]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def garden
  "This is the clojure-based version of CSS with which I'm most familiar.

Going with the assumption that it's appropriate and I'll be able to do something
useful with it here."
  s/Any)

(def om-world
  "Which data is absolutely required to define a world built around Om?"
  {:body html-in-edn
   (s/optional-key :css garden)
   :id s/Str   ; really, this should be a UUID. Probably one registered w/ a trusted [central?] authority
   ;; Q: Does it make any sense at all to try to define this with schema?
   (s/optional-key :script) s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(sm/defn pre-process
  [{:keys [body css id script] :as description} :- om-world]
  ;; TODO: Really need to parse/compile what came in.
  ;; cljs should be able to handle the :script part, although
  ;; life might be finicky because I really need to associate this
  ;; world's compiler state w/ a REPL under global.
  ;; Hopefully, something like garden can cope with the CSS (maybe on the
  ;; server side?)
  ;; After that, the fun seems to really begin w/ a translator from EDN to Om.
  (raise :not-implemented))
