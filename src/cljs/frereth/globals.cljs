(ns ^:figwheel-load frereth.globals)

;;;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {;; For managing REPLs
                          :text "Waiting for initial Connection to Client",
                          :compiler-state (comment (compiler/empty-state))
                          :repls [{:heading "Local"
                                   :output []
                                   :input "=>"
                                   :namespace "user"
                                   :state nil}]
                          ;; 3-d/graphics part
                          :renderer (fn []
                                      (.log js/console "Switch me out with something more interesting"))
                          ;; Interaction w/ client
                          :channel-socket nil
                          }))
