(ns ^:figwheel-always frereth.core
    "TODO: Do something interesting here"
    (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                     [schema.macros :as macros]
                     [schema.core])
    (:require [cljs.core.async :as async]
              [cljsjs.three]
              #_[cljsjs.gl-matrix]
              #_[cljsjs.d3]
              [frereth.globals :as global]
              [frereth.repl :as repl]
               #_[schema.core :as s]
              [taoensso.sente :as sente :refer (cb-success?)]))
(enable-console-print!)

(println "Top of core")

(defn event-handler
  [socket-description]
  (let [done (atom false)
        recv (:recv-chan socket-description)]
    (go-loop []
      (let [[incoming ch] (async/alts! [(async/timeout (* 1000 60 5)) recv])]
        (if (= recv ch)
          (do
            (println "Incoming message:\n" (:event incoming))
            (when-not incoming
              (println "Channel closed. We're done here")
              (reset! done true)))
          (do
            (println "Background Async WS Loop: Heartbeat"))))
      (when-not @done
        (recur)))))

(defn client-sock
  []
  (println "Building client connection")
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk"  ; Note path to request-handler on server
                                    {:type :auto})]
    (println "Client connection built")
    (let [result {:socket chsk
                  :recv-chan ch-recv
                  :send! send-fn
                  :state state}]
      (go
        (let [[handshake-response ch] (async/alts! [(async/timeout 5000) ch-recv])]
          (if (= ch ch-recv)
            (do
              (if-let [event (:event handshake-response)]
                (let [[kind body] event]
                  (println "Initial socket response:\n\t" kind
                           "\n\t" body)
                  (event-handler result))))
            (println "Timed out waiting for server response"))))
      result)))

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
      ;; Q: How do I position this over the REPL?
      (.appendChild (.-body js/document) (.-domElement renderer))
      (letfn [(render []
                      (set! (.-x (.-rotation mesh)) (+ (.-x (.-rotation mesh)) 0.01))
                      (set! (.-y (.-rotation mesh)) (+ (.-y (.-rotation mesh)) 0.02))
                      (.render renderer scene camera))
              (animate []
                       (.requestAnimationFrame js/window animate)
                       (render))]
        (animate)))))

(defn channel-swapper
  [current latest]
  (when-let [existing-ws-channel (:channel-socket current)]
    (let [receiver (:recv-chan existing-ws-channel)]
      (async/close! receiver)))
  (assoc current :channel-socket latest))

(defn start-graphics
  "TODO: Add a 2-D canvas HUD"
  [THREE]
  (start-3 THREE))

(defn -main
  []
  (repl/start)
  (start-graphics js/THREE)
  (swap! global/app-state  channel-swapper (client-sock)))
;; Because I'm not sure how to trigger this on a page reload
;; Really just for scaffolding: I won't want to do this after I'm
;; confident about setup/teardown
(-main)
