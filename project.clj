(defproject com.frereth/web "0.1.0-SNAPSHOT"
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
              :builds {:dev {
                             ;; Q: Does this belong here at all?
                             :figwheel {;; Optional callback
                                        ;; :on-jsreload "frereth.core/figwheel-reload"

                                        ;; Q: Is there a good way to make this just match my current IP?
                                        ;; A: Well, localhost would be a great choice, if that's where
                                        ;; I were actually running the browser.
                                        ;; Maybe it would be worth setting up DNS?
                                        :websocket-host #_"10.0.3.12" :js-client-host}
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
                                        :asset-path "js/compiled"
                                        ;;:source-map "resources/public/js/compiled/frereth.js.map"
                                        :source-map true
                                        :source-map-timestamp true
                                        :verbose true
                                        ;;:cache-analysis true
                                        }}
                       ;; TODO: Compare the output size of this vs. standard
                       ;; minification
                       :min {:source-paths ["src/cljs" "dev_src/cljs"]
                             :compiler {:output-to "resources/public/js/compiled/frereth.js"
                                        :main frereth.core
                                        ;; TODO: Advanced compilation has gone away
                                        ;; Actually, the entire google.clojure compiler has gone away
                                        ;; Q: Why am I getting errors from that?
                                        :optimizations :advanced
                                        :pretty-print false}}}}

  :dependencies [[org.clojure/clojure "1.8.0-RC4"] ; really should just inherit this from frereth-common

                 ;;; Libraries that are probably only useful server-side
                 [com.cognitect/transit-clj "0.8.285" :exclusions [com.fasterxml.jackson.core/jackson-core]] ; the cljs lib is totally different
                 ;; TODO: How many of these exclusions are still needed?
                 ;; And do more get added w/ this version bump?
                 [org.immutant/immutant "2.1.1" :exclusions [clj-tuple
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
                                                             ;org.slf4j/slf4j-api
                                                             riddley]]
                 ;; immutant is schizophrenic about which version it uses
                 #_[org.slf4j/slf4j-api "1.7.6"]  ; TODO: Try bumping to 12
                 ;; and about this
                 #_[org.jboss.logging/jboss-logging "3.3.0.Final"]
                 [org.jboss.logging/jboss-logging "3.2.1.Final"]
                 ;; May still need to fall back on
                 #_[org.jboss.logging/jboss-logging "3.1.4.GA"]

                 [prismatic/fnhouse "0.2.1" :exclusions [prismatic/plumbing prismatic/schema]]
                 [ring/ring-core "1.4.0" :exclusions [commons-codec
                                                          joda-time
                                                          org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 [ring/ring-anti-forgery "1.0.0" :exclusions [org.clojure/clojure]]
                 [ring/ring-defaults "0.1.5" :exclusions [org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 [ring/ring-devel "1.4.0"]  ; for stacktrace
                 [ring/ring-headers "0.1.3" :exclusions [org.clojure/clojure]]
                 [ring-middleware-format "0.7.0" :exclusions [org.clojure/clojure
                                                              org.clojure/core.memoize
                                                              org.clojure/tools.reader]]

                 ;;; Client-Specific...more or less
                 [cljsjs/three "0.0.72-0"]
                 ;; I'm fairly certain that I want this, though it's YAGNI
                 ;; at the moment.
                 ;; And something like pixi might be much nicer.
                 #_[cljsjs/d3 "3.5.5-3"]
                 [cljsjs/gl-matrix "2.3.0-jenanwise-0"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 ;; I'm getting a warning about no clojurescript dependency.
                 ;; Q: What did I mess up?
                 [org.clojure/clojurescript "1.7.189" :exclusions [org.clojure/clojure
                                                                   org.clojure/tools.reader]]

                 [org.omcljs/om "0.9.0" :exclusions [cljsjs/react
                                                     org.clojure/clojure]]
                 ;; These should be pulled in by Om's dependency on cljsjs/react
                 ;; Q: Why doesn't sablono see them?
                 [cljsjs/react-dom "0.14.3-1"]
                 [cljsjs/react-dom-server "0.14.3-0"]
                 [sablono "0.5.3"]
                 [secretary "1.2.3" :exclusions [org.clojure/clojure
                                                 org.clojure/clojurescript]]

                 ;;; Generally Useful
                 ;; Really should inherit my clojure version from this.
                 [com.frereth/client "0.1.0-SNAPSHOT"]
                 [com.taoensso/sente "1.7.0" :exclusions [com.taoensso/encore
                                                          com.taoensso/timbre
                                                          org.clojure/clojure
                                                          org.clojure/tools.reader]]
                 ;; This is a dependency that I'll be using at runtime to build
                 ;; the system that actually loads figwheel.
                 ;; Whereas figwheel is a plugin.
                 ;; I don't pretend to have a solid grasp on the distinction, but it's
                 ;; obviously an important one.
                 ;; Hopefully this has been successfully been moved to the dev dependency
                 #_[figwheel-sidecar "0.5.0-2" :exclusions [cider/cider-nrepl
                                                          com.stuartsierra/component
                                                          medley
                                                          org.clojure/clojure
                                                          org.clojure/clojurescript
                                                          org.clojure/java.classpath]]
                 [org.clojure/core.match "0.3.0-alpha4" :exclusions [org.clojure/clojure
                                                                     org.clojure/tools.analyzer.jvm]]]
  :description "Another waffle in my indecision about making this web-based"

  :figwheel {:css-dirs ["resources/public/css"] ;; watch and update CSS

             :http-server-root "public" ;; default and assumes "resources"

             :server-port 3449 ;; default

             ;; Start an nREPL server into the running figwheel process
             ;; I'm dubious. Q: What's the point?
             :nrepl-port 7888

             :reload-clj-files {:clj true :cljc true}

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

  ;; This was really for access to jzmq
  ;; Q: Does it serve any point if I'm not using that?
  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))]

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main ^:skip-aot com.frereth.web.core

  :plugins [[com.jakemccrary/lein-test-refresh "0.9.0"]
            [lein-cljsbuild "1.1.2" :exclusions [org.clojure/clojure]]
            ;; This is schizophrenic and conflicts with itself too
            ;; Specifically commons-fileupload and ring/ring-core
            ;; TODO: Keep an eye on those dependencies (esp) to see if I can get rid of
            ;; the ones that I really don't actually want
            [lein-figwheel "0.5.0-2" :exclusions [commons-fileupload  ; TODO: I probably need to include this myself now
                                                  org.codehaus.plexus/plexus-utils
                                                  org.clojure/clojure
                                                  org.clojure/tools.reader
                                                  ring/ring-core]]]

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
             ;:dev-I-want [:dev-base :figwheel]
             ;; For now, just do this by hand
             :dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.0-2" :exclusions [cider/cider-nrepl
                                                                           com.stuartsierra/component
                                                                           medley
                                                                           org.clojure/clojure
                                                                           org.clojure/clojurescript
                                                                           org.clojure/java.classpath]]]
                   :immutant {:context-path "/frereth"
                                   :nrepl-port 4242
                                   :lein-profiles [:dev]
                                   :env :dev}
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :resource-paths ["dev-resources"]
                   :source-paths ["dev" "src/cljs" "dev_src/cljs"]}}
  :repl-options {:init-ns user
                 :timeout 120000
                 :welcome (println "Run (dev) then (reset) to begin")}
  :resource-paths ["resources" "target/js"]
  :source-paths ["src/clj"]
  :target-path "target/%s"
  :url "http://frereth.com")
