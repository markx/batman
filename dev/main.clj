(ns main
  (:require [batman.core :as batman]
            [nrepl.cmdline :as nrepl-cmdline]))



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
 (batman/start)
 (System/exit 0))
