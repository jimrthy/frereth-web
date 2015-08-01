(ns ^:figwheel-load frereth.globals)

;;;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Waiting for initial Connection to Client",
                          :compiler-state (comment (compiler/empty-state))
                          :repls [{:heading "Local"
                                   :output []
                                   :input "=>"
                                   :namespace "user"
                                   :state nil}]}))
