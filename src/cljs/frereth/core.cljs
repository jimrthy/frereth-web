(ns ^:figwheel-always frereth.core
    "TODO: Do something interesting here"
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go)]
                     [schema.macros :as macros]
                     [schema.core])
    (:require #_[schema.core :as s]
              [cljsjs.three]
              #_[cljsjs.gl-matrix]
              #_[cljsjs.d3]
              [frereth.repl :as repl]
              [taoensso.sente :as sente :refer (cb-success?)]))
(enable-console-print!)

(println "Top of core")

  ;; define your app data so that it doesn't get over-written on reload


(defonce app-state (atom {:text "Hello from cljsjs!"}))

(comment (let [v1 (.fromValues js/vec3 1 10 100)
               v2 (.fromValues js/vec3 1 1 1)
               v3 (.create js/vec3)]
           (.add js/vec3 v3 v1 v2)
           (println (.str js/vec3 v1)
                    "+" (.str js/vec3 v2)
                    "=" (.str js/vec3 v3))))

(defn client-sock
  []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk"  ; Note path to request-handler on server
                                    {:type :auto})]
    {:socket chsk
     :recv-chan ch-recv
     :send! send-fn
     :state state}))

(defn start-3
  [THREE]
  (let [camera (THREE.PerspectiveCamera. 75 (/ (.-innerWidth js/window)
                                               (.-innerHeight js/window)) 1 10000)
        scene (THREE.Scene.)
        geometry (THREE.BoxGeometry. 200 200 200)
        obj (js/Object.)]
    (set! (.-z (.-position camera)) 10000)
    (set! (.-color obj) 0xff0000)
    (set! (.-wireframe obj) true)
    (let [material (THREE.MeshBasicMaterial. obj)
          mesh (THREE.Mesh. geometry material)
          ;; I really want a 2-d renderer for this
          ;; That used to be a CanvasRenderer
          ;; But apparently that's been deprecated back to a demo
          renderer (THREE.WebGLRenderer.)]
      (.add scene mesh)
      (.setSize renderer (.-innerWidth js/window) (.-innerHeight js/window))
      (.appendChild (.-body js/document) (.-domElement renderer))
      (letfn [(render []
                      (set! (.-x (.-rotation mesh)) (+ (.-x (.-rotation mesh)) 0.01))
                      (set! (.-y (.-rotation mesh)) (+ (.-y (.-rotation mesh)) 0.02))
                      (.render renderer scene camera))
              (animate []
                       (.requestAnimationFrame js/window animate)
                       (render))]
        (animate)))))

(repl/start)
(start-3 js/THREE)
(swap! app-state  assoc current :channel-socket (client-sock))

(defn reflect  ; :- {s/Keyword s/Any}
  "TODO: This doesn't belong here. But it's probably a better
location than common.clj where I was storing it.
It's tempting to change that to common.cljc, but I'm not
sure there's enough cross-platform functionality to make that
worthwhile.

Return a clojurescript map version of a javascript object
It seems like js->clj should already handle this, but
I haven't had very good luck with it"
  [o]
  (let [props (.keys js/Object o)]
    (reduce (fn [acc prop]
              (assoc acc (keyword prop) (aget o prop)))
            {}
            props)))
