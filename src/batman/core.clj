(ns batman.core
  (:gen-class)
  (:require [clj-telnet.core :as telnet]))


(def conn (atom nil))

(defn start-conn [host port]
  (reset! conn (telnet/get-telnet host port)))

(defn stop-conn []
  (when @conn
    (telnet/kill-telnet @conn)
    (reset! conn nil)))

(defn conn-loop [c]
  (print (telnet/read-until-or c ["\n"] 500))
  (flush)
  (recur c))



(defn run []
  (future (conn-loop (start-conn "bat.org" 23)))
  (loop []
    (telnet/write @conn (read-line))
    (recur)))



(defn -main []
  (run))

