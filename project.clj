(defproject com.frereth/web "0.1.0-SNAPSHOT"
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
    :builds [{:id "dev"
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

  :dependencies [[org.clojure/clojure "1.9.0-alpha11"]  ; TODO: Make this go back away

                 ;; Probably only useful server-side
                 [com.cognitect/transit-clj "0.8.288" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 ;; TODO: How many of these exclusions are still needed?
                 ;; And do more get added w/ this version bump?
                 [org.immutant/immutant "2.1.5" :exclusions [clj-tuple
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
                                                             riddley]]
                 ;; Q: Do I still need this?
                 ;; (it's up to 3.3.0.Final...how far can I successfully push it?)
                 ;; Some parts of it depend on this version, others on 3.2.1.GA
                 [org.jboss.logging/jboss-logging "3.1.4.GA"]
                 [prismatic/fnhouse "0.2.1" :exclusions [prismatic/plumbing
                                                         prismatic/schema]]
                 [ring/ring-core "1.5.0" :exclusions [commons-codec
                                                      joda-time
                                                      org.clojure/clojure
                                                      org.clojure/tools.reader]]
                 [ring/ring-devel "1.5.0" :exclusions [org.clojure/tools.namespace]]
                 [ring/ring-anti-forgery "1.0.1" :exclusions [org.clojure/clojure]]
                 [ring/ring-defaults "0.2.1" :exclusions [org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 [ring/ring-headers "0.2.0" :exclusions [org.clojure/clojure]]
                 [ring-middleware-format "0.7.0" :exclusions [com.cognitect/transit-clj
                                                              org.clojure/clojure
                                                              org.clojure/core.memoize
                                                              org.clojure/tools.reader]]

                 ;;; Client-Specific...more or less
                 [cljsjs/three "0.0.76-0"]
                 #_[cljsjs/d3 "3.5.5-3"]
                 [cljsjs/gl-matrix "2.3.0-jenanwise-0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 ;; Q: Is there any remaining use for this?
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [org.clojure/clojurescript "1.9.229" :exclusions [org.clojure/clojure
                                                                   org.clojure/tools.reader]]
                 ;; TODO: This needs to go away.
                 ;; It's up to the individual Apps.
                 [org.omcljs/om "0.9.0" :exclusions [org.clojure/clojure]]
                 ;; TODO: This needs to go away.
                 ;; It's up to the individual Apps.
                 [sablono "0.7.4"]
                 ;; TODO: Switch to bidi
                 ;; (it's built around plain data rather than macros
                 [secretary "1.2.3" :exclusions [org.clojure/clojure
                                                 org.clojure/clojurescript]]

                 ;;; Generally Useful
                 ;; Really should inherit my clojure version from this.
                 [com.frereth/client "0.1.0-SNAPSHOT"]
                 [com.taoensso/sente "1.10.0" :exclusions [com.taoensso/encore
                                                           com.taoensso/timbre
                                                           org.clojure/clojure
                                                           org.clojure/core.async
                                                           org.clojure/tools.reader]]
                 #_[figwheel "0.5.4-3" :exclusions [cider/cider-nrepl
                                                    org.clojure/clojure
                                                    org.clojure/clojurescript
                                                    org.clojure/tools.reader
                                                    ring/ring-core]]
                 #_[figwheel-sidecar "0.5.4-3" :exclusions [cider/cider-nrepl
                                                            org.clojure/clojure
                                                            org.clojure/clojurescript
                                                            org.clojure/java.classpath]]
                 [org.clojure/core.match "0.2.2" :exclusions [org.clojure/clojure
                                                              org.clojure/core.cache
                                                              org.clojure/core.memoize
                                                              org.clojure/data.priority-map
                                                              org.clojure/tools.analyzer.jvm
                                                              org.ow2.asm/asm-all]]]
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

  :plugins [[com.jakemccrary/lein-test-refresh "0.9.0"]
            [lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.7" :exclusions [cider/cider-nrepl
                                                org.clojure/clojure
                                                org.clojure/clojurescript
                                                org.clojure/tools.reader
                                                ;;org.codehaus.plexus/plexus-utils
                                                ring/ring-core]]]

  :profiles {:dev-base {:immutant {:context-path "/frereth"
                                   :nrepl-port 4242
                                   :lein-profiles [:dev]
                                   :env :dev}
                        :resource-paths ["dev-resources"]
                        :source-paths ["dev"]}
             :figwheel {:dependencies []
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
