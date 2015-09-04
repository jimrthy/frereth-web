(ns ^:figwheel-load frereth.globals)

;;;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {;; Part the renderer will use to decide what to draw
                          :worlds {:splash {:state :initializing
                                            :repl {:heading "Local"
                                                   :output []
                                                   :input "=>"
                                                   :namespace "user"
                                                   :state nil}
                                            :renderer 'frereth.three/splash-screen}}
                          ;; World to draw
                          ;; Note that, really, this needs to be controlled by
                          ;; something like an X Window Manager.
                          ;; Could very well have many visible active worlds at
                          ;; the same time.
                          ;; That doesn't seem wise, but it's definitely possible.
                          :active-world :splash
                          ;; Interaction w/ client
                          ;; Yes, this really is singular:
                          ;; renders *->1 client
                          ;; client 1->* servers
                          :channel-socket nil}))
