(ns batman.core
  (:gen-class)
  (:require [clj-telnet.core :as telnet]
            [nrepl.cmdline :as nrepl-cmdline]
            [clojure.core.async :as async
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]))


(defonce conn (atom nil))

(defn start-conn [host port]
  (reset! conn (telnet/get-telnet host port)))

(defn stop-conn []
  (when @conn
    (telnet/kill-telnet @conn)
    (reset! conn nil)))


(defn read-line-or-available [conn]
  (let [in (.getInputStream conn)]
    (loop [result ""]
      (let [s (char (.read in))]
        (if (or  (= s \newline) (>= 0 (.available in)))
          (str result s)
          (recur (str result s)))))))


(defn handle-message [m c]
  (print m)
  (flush))

(defn conn-loop [quit c]
  (when (not (realized? quit))
    (try
      (-> (read-line-or-available c)
          (handle-message c))
      (catch Exception e 
        (prn e)
        (deliver quit true)))
    (recur quit c)))


(defn handle-input [c l]
  (println "input: " (pr-str l))
  (telnet/write c l))

(defn input-loop [quit c]
  (loop []
    (when (not (realized? quit))
      (when-let [l (read-line)] 
        (handle-input c l)
        (recur))))
  (println "input done")
  (deliver quit true))

(defn start []
  (let [c (start-conn "bat.org" 23)
          quit (promise)]
    (thread 
      (input-loop quit c))
    (thread  
      (conn-loop quit c))
    @quit
    (stop-conn)))


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
  (start)
  (System/exit 0))

(comment 
  (def c (start-conn "bat.org" 23))
  (defn bar []
    (future))
  (Thread/sleep 1000))

