(ns frereth.three
  (:require [cljsjs.three]
            #_[cljsjs.gl-matrix]
            #_[cljsjs.d3]
            [frereth.globals :as global]
            [schema.core :as s :include-macros true]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def legal-splash-states
  "This should really be an enum"
  s/Any)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn create-renderer
  [THREE]
  (let [camera (THREE.PerspectiveCamera. 75 (/ (.-innerWidth js/window)
                                               (.-innerHeight js/window)) 1 10000)
        scene (THREE.Scene.)
        geometry (THREE.BoxGeometry. 200 200 200)
        obj (js/Object.)]
    (set! (.-z (.-position camera)) 1000)
    (set! (.-color obj) 0xff0000)
    (set! (.-wireframe obj) true)
    (let [material (THREE.MeshBasicMaterial. obj)
          mesh (THREE.Mesh. geometry material)
          canvas (.getElementById js/document "view")
          ;; I really want a 2-d renderer for this
          ;; That used to be a CanvasRenderer
          ;; But apparently that's been deprecated back to a demo
          ;; Q: Does this create the correct kind of javascript object?
          renderer (THREE.WebGLRenderer. #js {:canvas canvas})]
      (.add scene mesh)
      (.setSize renderer (.-innerWidth js/window) (.-innerHeight js/window))

      ;; This is the part that actually does the drawing.
      ;; TODO: Should probably refactor this so the renderer is one thing,
      ;; the rendering-fn the part that gets swapped out all the time
      (fn []
        ;; TODO: Only change the color when state changes
        (comment (when (= (global/get-world-state :splash) :connecting)
                   ;; This doesn't seem to work.
                   ;; Q: Is it too late after the material has been assigned to the scene?
                   (log/debug "Switching cube color to yellow")
                   (set! (.-color obj) 0xffff00)))
        (set! (.-x (.-rotation mesh)) (+ (.-x (.-rotation mesh)) 0.01))
        (set! (.-y (.-rotation mesh)) (+ (.-y (.-rotation mesh)) 0.02))
        (.render renderer scene camera)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn start-graphics
  "TODO: Add a 2-D canvas HUD"
  [THREE]
  (let [renderer (create-renderer THREE)]
    (letfn [(animate []
                     (let [active-world (global/get-active-world)]
                       (if (or (nil? active-world)
                               ;; TODO: The :splash world should be gone
                               (= active-world :splash))
                         (do
                           (.requestAnimationFrame js/window animate)
                           (renderer))
                         (log/error "TODO: Watch for world state returning to splash"))))]
      (animate))))
