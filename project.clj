(defproject batman "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-telnet "0.3.0"]
                 [nrepl "0.6.0"]
                 [cider/cider-nrepl "0.21.1"]
                 [com.taoensso/timbre "4.10.0"]]
  :plugins [[cider/cider-nrepl "0.21.1"]]
  :main ^:skip-aot batman.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
