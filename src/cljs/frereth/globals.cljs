(ns ^:figwheel-load frereth.globals)

;;;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {;; For managing REPLs
                          :repls {:text "Waiting for initial Connection to Client",
                                  :compiler-state (comment (compiler/empty-state))
                                  :repls [{:heading "Local"
                                           :output []
                                           :input "=>"
                                           :namespace "user"
                                           :state nil}]}
                          ;; 3-d/graphics part
                          ;; This should be a function that accepts a state parameter and produces side-effects
                          ;; That feels wrong. It should really return some HTML to use as a background, and
                          ;; draw side-effects onto the global canvas
                          :renderer nil
                          ;; Part the renderer will use to decide what to draw
                          :world-state {}
                          ;; Interaction w/ client
                          :channel-socket nil
                          }))
