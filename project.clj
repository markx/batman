(defproject batman "0.2.0"
  :description "A BatMUD client."
  :url "https://github.com/markx/batman"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[cider/cider-nrepl "0.21.1"]
            [lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project "deps.edn"]}
  :main batman.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :main main
                   :dependencies [[nrepl "0.6.0"]
                                  [cider/cider-nrepl "0.21.1"]]}})
