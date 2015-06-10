(ns frereth-common.util
  "Started out life as frereth-common.util from that library.

Slightly enhanced."
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [puget.printer :as puget]
            [ribol.core :refer [raise]]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [java.io PushbackReader]
           [java.lang.reflect Modifier]
           [java.util Collection Date UUID]))

(comment (raise :obsolete))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; TODO

;; Global function calls like this are bad.
;; Especially since I'm looking at a
;; white terminal background, as opposed to what
;; most seem to expect
;; TODO: Put this inside a component's start
;; instead
;; Q: Where'd this function call go?
(comment (puget/set-color-scheme! :keyword [:bold :green]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn cheat-sheet
  "Shamelessly borrowed from https://groups.google.com/forum/#!topic/clojure/j5PmMuhG3d8"
  [ns]
  (raise {:obsolete "Use frereth.common instead"})
  (let [nsname (str ns)
        vars (vals (ns-publics ns))
        {funs true
         defs false} (group-by nil? vars)
        fmeta (map meta funs)
        dmeta (map meta defs)
        flen (apply max 0 (map (comp count str :name) fmeta))
        dnames (map #(str nsname \/ (:name %)) dmeta)
        fnames (map #(format (str "%s/%-" flen "s %s") nsname (:name %)
                             (string/join \space (:arglists %)))
                    fmeta)
        lines (concat (sort dnames) (sort fnames))]
    (str ";;; " nsname " {{{1\n\n"
         (string/join \newline lines))))

(comment
  (defn dir
    [something]
    (raise {:obsolete "Use frereth.common instead"})
    (let [k (class something)
          bases (.getClasses k)
          fields (.getDeclaredFields k)
          useful-fields (map describe-field fields)
          methods (.getDeclaredMethods k)
          useful-methods (map describe-method methods)]
      ;; There are a bunch of associated predicates, but they don't seem all that useful
      ;; yet.
      ;; Things like isInterface
      {:bases bases
       :declared-bases (.getDeclaredClasses k)   ; I have serious doubts about this' usefulness
       :canonical-name (.getCanonicalName k)
       :class-loader (.getClassLoader k)
       :fields useful-fields
       :methods useful-methods
       :owner (.getDeclaringClass k)
       :encloser (.getEnclosingClass k)
       :enums (.getEnumConstants k)  ; seems dubiously useless...except when it's needed
       :package (.getPackage k)
       :protection-domain (.getProtectionDomain k)
       :signers (.getSigners k)
       :simple-name (.getSimpleName k)
       ;; Definitely deserves more detail...except that this is mostly useless
       ;; in the clojure world
       :type-params (.getTypeParameters k)})))

(defn pretty
  [& os]
  #_[o]
  (raise {:obsolete "Use frereth.common instead"}))

(s/defn pushback-reader :- PushbackReader
  "Probably belongs under something like utils.
Yes, it does seem pretty stupid"
  [reader]
  (raise {:obsolete "Use frereth.common instead"})
  (PushbackReader. reader))

(s/defn random-uuid :- UUID
  "Because remembering the java namespace is annoying"
  []
  (raise {:obsolete "Use frereth.common instead"})
  (UUID/randomUUID))
