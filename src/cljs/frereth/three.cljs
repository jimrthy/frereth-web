(ns frereth.three
  (:require-macros [schema.macros :as macros]
                   [schema.core])
  (:require [cljsjs.three]
            #_[cljsjs.gl-matrix]
            #_[cljsjs.d3]
            [frereth.globals :as global]))

(defn create-renderer
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
      ;; Q: How do I position this over the REPL?
      ;; Maybe more importantly, how do I just use an existing canvas?
      (.appendChild (.-body js/document) (.-domElement renderer))

      ;; This is the part that actually does the drawing.
      ;; TODO: Should probably refactor this so the renderer is one thing,
      ;; the rendering-fn the part that gets swapped out all the time
      (fn []
        (set! (.-x (.-rotation mesh)) (+ (.-x (.-rotation mesh)) 0.01))
        (set! (.-y (.-rotation mesh)) (+ (.-y (.-rotation mesh)) 0.02))
        (.render renderer scene camera)))))

(defn start-graphics
  "TODO: Add a 2-D canvas HUD"
  [THREE]
  (swap! global/app-state assoc :renderer (create-renderer THREE))
  (letfn [(animate []
                     (let [render (-> global/app-state deref :renderer)]
                       (.requestAnimationFrame js/window animate)
                       (render)))]
    (animate))
  (create-renderer THREE))