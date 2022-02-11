(defproject musnake "0.1.0"
  :description "A Multiplayer Snake game for you to have fun with your friends!"
  :url "https://musnake.herokuapp.com/"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :min-lein-version "2.9.1"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async  "0.4.500"]
                 [compojure "1.6.1"]
                 [devcards "0.2.7"]
                 [http-kit "2.5.3"]
                 [jarohen/chord "0.8.1"]
                 [medley "1.3.0"]
                 [reagent "0.10.0"]]

  :plugins [[lein-figwheel "0.5.20" :exclusions [[http-kit]]]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [com.github.clj-kondo/lein-clj-kondo "0.1.3"]]

  :source-paths ["src"]
  :test-paths ["src"]
  :uberjar-name "musnake-standalone.jar"
  :resource-paths ["resources"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src" "dev"]

                ;; The presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "musnake.client.core/on-js-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and compiled your application.
                           ;; Comment this out once it no longer serves you.
                           :open-urls ["http://localhost:3449/index.html"]}

                :compiler {:main musnake.client.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/musnake.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]}}

               {:id "cards"
                :source-paths ["src" "dev"]
                :figwheel {:devcards true
                           :open-urls ["http://localhost:3449/cards.html"]}
                :compiler {:main       musnake.client.views
                           :asset-path "js/compiled/cards_out"
                           :output-to  "resources/public/js/compiled/cards.js"
                           :output-dir "resources/public/js/compiled/cards_out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               ;; This next build is a compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/musnake.js"
                           :main musnake.client.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel { ;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; nrepl-port 7888

             :builds-to-start ["dev" "cards"]

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             :ring-handler musnake.server.handler/app

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"

             ;; to pipe all the output to the repl
             :server-logfile false
             }

  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.0"]
                                  [figwheel-sidecar "0.5.20" :exclusions [[http-kit]]]]
                   ;;:repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   ;; need to add the compiled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                                     :target-path]}
             :uberjar {:prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :aot :all
                       :main musnake.server.main}})
