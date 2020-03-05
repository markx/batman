(ns main
  (:require [batman.core :as batman]
            [nrepl.cmdline :as nrepl-cmdline]
            [rebel-readline.core]
            [rebel-readline.clojure.line-reader]
            [rebel-readline.clojure.service.local]
            [rebel-readline.clojure.main]))



(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-nrepl-server []
  "start a nrepl server for dev/debugging"
  (let [nrepl (nrepl-cmdline/start-server {:handler (nrepl-handler)})]
    (nrepl-cmdline/save-port-file nrepl nil)
    (println (format "nREPL server started on port %d" (:port nrepl)))))

(defn -main []
  (start-nrepl-server)

  (in-ns 'batman.core)
  (rebel-readline.core/with-line-reader
    (rebel-readline.clojure.line-reader/create
     (rebel-readline.clojure.service.local/create))
    (clojure.main/repl
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.clojure.main/create-repl-read))))
