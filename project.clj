(defproject com.frereth/web "0.1.0-SNAPSHOT"
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
    :builds [{:id "dev"
              ;; Q: Is this still relevant?
              ;; TODO: Check the figwheel docs to see what this should look like
              ;; Doubt there's any way to avoid it, but it is annoyingly specific
              ;; without some sort of DNS
              :figwheel {:websocket-host "10.0.3.152"}
              :source-paths ["src/cljs" "dev_src/cljs"]
              ;; Different output targets should go to different paths
              ;; Should probably have a different index.?.html based on build
              ;; profile.
              ;; Then the route middleware that returns the index could return
              ;; the one based on build profile (assuming that info's available
              ;; at run-time)
              :compiler {:output-to "resources/public/js/compiled/frereth.js"
                         :output-dir "resources/public/js/compiled"
                         :optimizations :none
                         :main frereth.core   ; Q: Huh?
                         ;;:main frereth.core
                         :asset-path "js/compiled"
                         ;;:source-map "resources/public/js/compiled/frereth.js.map"
                         :source-map true
                         :source-map-timestamp true
                         :verbose true
                         ;;:cache-analysis true
                         }}
             ;; TODO: Compare the output size of this vs. standard
             ;; minification
             #_{:id "min"
              :source-paths ["src"]
              :compiler {:output-to "resources/public/js/compiled/frereth.js"
                         :main frereth.core
                         ;; TODO: Advanced compilation has gone away
                         ;; Actually, the entire google.clojure compiler has gone away
                         ;; Q: Why am I getting errors from that?
                         :optimizations :advanced
                         :pretty-print false}}]}

  :dependencies [[org.clojure/clojure "1.8.0-RC4"] ; absolutely should not need this (i.e. should inherit from frereth.common)

                 ;;; Libraries that are probably only useful server-side
                 [com.cognitect/transit-clj "0.8.283"]
                 ;; TODO: How many of these exclusions are still needed?
                 ;; And do more get added w/ this version bump?
                 [org.immutant/immutant "2.1.0" :exclusions [clj-tuple
                                                             org.clojure/clojure
                                                             org.clojure/java.classpath
                                                             org.clojure/tools.reader
                                                             org.hornetq/hornetq-commons
                                                             org.hornetq/hornetq-core-client
                                                             org.hornetq/hornetq-journal
                                                             org.hornetq/hornetq-native
                                                             org.hornetq/hornetq-server
                                                             org.jboss.logging/jboss-logging
                                                             org.jgroups/jgroups
                                                             org.slf4j/slf4j-api
                                                             riddley]]
                 ;; immutant is schizophrenic about which version it uses
                 [org.slf4j/slf4j-api "1.7.6"]  ; TODO: Try bumping to 12
                 ;; and about this
                 #_[org.jboss.logging/jboss-logging "3.2.1.Final"]
                 [org.jboss.logging/jboss-logging "3.1.4.GA"]

                 [prismatic/fnhouse "0.2.1" :exclusions [prismatic/plumbing]]
                 [ring/ring-core "1.4.0" :exclusions [commons-codec
                                                          joda-time
                                                          org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 [ring/ring-anti-forgery "1.0.0" :exclusions [org.clojure/clojure]]
                 [ring/ring-defaults "0.1.5" :exclusions [org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 [ring/ring-headers "0.1.3" :exclusions [org.clojure/clojure]]
                 [ring-middleware-format "0.6.0" :exclusions [org.clojure/clojure
                                                              org.clojure/tools.reader]]

                 ;;; Client-Specific...more or less
                 [cljsjs/three "0.0.70-0"]
                 #_[cljsjs/d3 "3.5.5-3"]
                 [cljsjs/gl-matrix "2.3.0-jenanwise-0"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [org.clojure/clojurescript "1.7.170" :exclusions [org.clojure/clojure
                                                                   org.clojure/tools.reader]]
                 [org.omcljs/om "0.9.0" :exclusions [org.clojure/clojure]]
                 [sablono "0.3.6"]
                 [secretary "1.2.3" :exclusions [org.clojure/clojure
                                                 org.clojure/clojurescript]]

                 ;;; Generally Useful
                 ;; Really should inherit my clojure version from this.
                 [com.frereth/client "0.1.0-SNAPSHOT"]
                 [com.taoensso/sente "1.7.0-RC1" :exclusions [com.taoensso/timbre
                                                              org.clojure/clojure
                                                              org.clojure/tools.reader]]
                 [figwheel "0.4.1" :exclusions [cider/cider-nrepl
                                                org.clojure/clojure
                                                org.clojure/clojurescript]]
                 ;; Definitely shouldn't need this, since figwheel already depends on it
                 [figwheel-sidecar "0.4.1" :exclusions [cider/cider-nrepl
                                                        org.clojure/clojure
                                                        org.clojure/clojurescript
                                                        org.clojure/java.classpath]]
                 [org.clojure/core.match "0.3.0-alpha4" :exclusions [org.clojure/clojure]]]
  :description "Another waffle in my indecision about making this web-based"

  :figwheel {
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             :http-server-root "public" ;; default and assumes "resources"

             :server-port 3449 ;; default

             ;; Start an nREPL server into the running figwheel process
             ;; I'm dubious. Q: What's the point?
             :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }

  :immutant {:init "frereth-web.core/-main"}

  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))]

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main ^:skip-aot com.frereth.web.core

  :plugins [[cider/cider-nrepl "0.9.1"] ; shouldn't need to do this. No idea what's pulling in 0.8.2.
            [com.jakemccrary/lein-test-refresh "0.9.0"]
            [lein-cljsbuild "1.1.2" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.3.5" :exclusions [org.codehaus.plexus/plexus-utils
                                                org.clojure/clojure]]]

  :profiles {:dev-base {:immutant {:context-path "/frereth"
                                   :nrepl-port 4242
                                   :lein-profiles [:dev]
                                   :env :dev}
                        :plugins [#_[lein-figwheel "0.3.3" :exclusions [org.clojure/clojurescript
                                                                        org.codehaus.plexus/plexus-utils]]
                                  #_[com.jakemccrary/lein-test-refresh "0.9.0"]]
                        :resource-paths ["dev-resources"]
                        :source-paths ["dev"]}
             :figwheel {:dependencies [#_[figwheel "0.3.3"]]
                        :figwheel {:css-dirs ["resources/public/css"]
                                   :resource-paths ["target/js"]}}
             ;; Q: Why isn't this working?
             :dev [:dev-base :figwheel]}
  :repl-options {:init-ns user
                 :timeout 120000
                 :welcome (println "Run (dev) then (reset) to begin")}
  :resource-paths ["resources" "target/js"]
  :source-paths ["src/clj"]
  :target-path "target/%s"
  :url "http://frereth.com")
