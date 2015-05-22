(ns frereth-common.util
  "Started out life as frereth-common.util from that library.

Slightly enhanced."
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [puget.printer :as puget]
            [ribol.core :refer [raise]]
            [schema.core :as s])
  (:import [java.io PushbackReader]
           [java.lang.reflect Modifier]
           [java.util Collection Date UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def async-channel (class (async/chan)))
(def java-byte-array (Class/forName "[B"))
;; FIXME: This should probably come from something like
;; simple-time instead
(def time-stamp Date)

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

(defn describe-annotations
  [as]
  (map #(.toString %) as))

(s/defn interpret-modifiers :- [s/Keyword]
  [ms :- s/Int]
  ;; TODO: this implementation sucks
  (let [dict {Modifier/ABSTRACT :abstract
              Modifier/FINAL :final
              Modifier/INTERFACE :interface
              Modifier/NATIVE :native
              Modifier/PRIVATE :private
              Modifier/PROTECTED :protected
              Modifier/PUBLIC :public
              Modifier/STATIC :static
              Modifier/STRICT :strict
              Modifier/SYNCHRONIZED :synchronized
              Modifier/TRANSIENT :transient
              Modifier/VOLATILE :volatile}]
    (reduce (fn [acc [k v]]
              (comment (println "Thinking about assoc'ing " v " with " acc))
              (if (not= 0 (bit-and ms k))
                (conj acc v)
                acc))
            [] dict)))

(defn describe-field
  [f]
  {:name (.getName f)
   :annotations (map describe-annotations (.getDeclaredAnnotations f))
   :modifiers (interpret-modifiers (.getModifiers f))})

(defn describe-method
  [m]
  {:annotations (map describe-annotations (.getDeclaredAnnotations m))
   :exceptions (.getExceptionTypes m)
   :modifiers (interpret-modifiers (.getModifiers m))
   :return-type (.getReturnType m)
   :name (.toGenericString m)})

(defn fn-var?
  [v]
  (let [f @v]
    (or (contains? (meta v) :arglists)
        (fn? f)
        (instance? clojure.lang.MultiFn f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn cheat-sheet
  "Shamelessly borrowed from https://groups.google.com/forum/#!topic/clojure/j5PmMuhG3d8"
  [ns]
  (let [nsname (str ns)
        vars (vals (ns-publics ns))
        {funs true
         defs false} (group-by fn-var? vars)
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

(defn dir
  [something]
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
     :type-params (.getTypeParameters k)}))

(s/defn ^:always-validate load-resource
  [url :- s/Str]
  (-> url
      clojure.java.io/resource
      slurp
      edn/read-string))

(defn pick-home
  "Returns the current user's home directory"
  []
  (System/getProperty "user.home"))

(defn pretty
  [& os]
  (with-out-str (apply puget/cprint os)))

(s/defn pushback-reader :- PushbackReader
  "Probably belongs under something like utils.
Yes, it does seem pretty stupid"
  [reader]
  (PushbackReader. reader))

(s/defn random-uuid :- UUID
  "Because remembering the java namespace is annoying"
  []
  (UUID/randomUUID))
