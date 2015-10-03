(ns frereth.schema
  "For schema that's shared pretty much everywhere, to avoid circular imports

TODO: Honestly, these pieces probably belong in frereth.common in a .cljc"
  (:require [schema.core :as s :include-macros true]))

(def compiler-black-box
  "Doesn't really belong in here, but it'll do as a start"
  s/Any)

(def zmq-protocols (s/enum :tcp))

;; After I figure out how to get it to actually work
(def world-id #_(s/conditional s/Keyword s/Str s/Uuid) s/Any)

(def world-url {:protocol zmq-protocols
          ;; At first, at least, address will almost
          ;; always be a dotted quad.
          ;; DNS should get involved soon enough
          ;; Even then...it's still host.subdomain...domain.tld
          :address s/Str
          :port s/Int
          :path s/Str})

(def world-template
  "What does the highest possible view of a World look like?"
  {:id world-id  ; TODO: Refactor/rename this to something like :uid, so I can use destructuring w/ it
   :compiler-state compiler-black-box
   :url world-url})
