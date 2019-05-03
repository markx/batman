(ns batman.core
  (:gen-class)
  (:require [clj-telnet.core :as telnet]
            [nrepl.cmdline :as nrepl-cmdline]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(def log-file "log.txt")
(log/merge-config!
  {:appenders
    {:spit (appenders/spit-appender {:fname log-file})
     :println {:enabled? false}}})

(defonce conn (atom nil))
(defonce triggers (atom []))

(defn start-conn [host port]
  (reset! conn (telnet/get-telnet host port)))

(defn stop-conn []
  (when @conn
    (telnet/kill-telnet @conn)
    (reset! conn nil)))


(defn read-line-or-available [conn]
  (let [in (.getInputStream conn)]
    (loop [result ""]
      (let [b (.read in)]
        (if (= b -1) ;EOF
          (throw (java.io.EOFException. "EOF disconnected."))
          (let [c (char b)]
            (if (or  (= c \newline) (>= 0 (.available in)))
              (str result c)
              (recur (str result c)))))))))

(defn message [s]
  {:message s
   :gag false})


(defn apply-triggers [msg]
  (reduce
    (fn [m f]
      (or (f m) m))
    msg
    @triggers))


(defn print-message [m]
  (when (and (:message m) 
             (not (:gag m)))
    (print (:message m))
    (flush))
  m)

(defn handle-message [m]
  (-> m
      (apply-triggers)
      (print-message)))

(defn conn-loop [quit c]
  (when (not (realized? quit))
    (try
      (-> (read-line-or-available c)
          (message)
          (handle-message)) 
      (catch java.io.IOException e
        (println (.getMessage e))
        (deliver quit true))
      (catch Exception e
        (prn e)
        (deliver quit true)))
    (recur quit c)))


(defn handle-input [c l]
  (log/info "input: " (pr-str l))
  (telnet/write c l))

(defn input-loop [quit c]
  (loop []
    (when (not (realized? quit))
      (when-let [l (read-line)]
        (handle-input c l)
        (recur))))
  (deliver quit true))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-nrepl-server []
  "start a nrepl server for dev/debugging"
  (let [nrepl (nrepl-cmdline/start-server {:handler (nrepl-handler)})]
    (nrepl-cmdline/save-port-file nrepl nil)
    (println (format "nREPL server started on port %d" (:port nrepl)))))


(defn write-to [f & s]
  (spit f (apply print-str s)))

(defn send-cmd [s]
  (telnet/write @conn s))

(defn gag [m]
  (assoc m :gag true))

(defn inject-utils [ns]
  (doseq [[k f] {'send-cmd send-cmd
                 'write-to write-to
                 'gag gag}]
    (intern ns k f)))


(defn register-trigger! [f]
  (swap! triggers conj f))


(defn load-script [path]
  (let [name (-> path
                 (clojure.string/replace #"\.clj$" "")
                 (clojure.string/replace #"/" "."))
        space (create-ns (symbol name))]
    (inject-utils space)
    (binding [*ns* space]
      (clojure.core/refer-clojure)
      (load-file path)
      (log/info "created ns: " name)

      (when-let [setup (resolve (symbol name "setup"))]
        (@setup))
      (when-let [u (resolve (symbol name "update"))]
       (register-trigger! @u)
       (log/info "regiestered update " (ns-name space))
       (log/info "triggers" @triggers)))))

(defn load-scripts
  ([] (load-scripts "scripts/"))
  ([path] (run!  #(load-script (.getPath %))
                 (filter (fn [file]
                           (and (.isFile file)
                                (re-find #"\.clj$" (.getName file))))
                         (file-seq (clojure.java.io/file path))))))


(defn reload-scripts []
  (reset! triggers [])
  (load-scripts))


(defn start []
  (let [c (start-conn "bat.org" 23)
          quit (promise)]
    (log/info "connected to server" c)
    (load-scripts)
    (future
      (input-loop quit c))
    (future
      (conn-loop quit c))
    @quit
    (stop-conn)))


(defn -main []
  (start-nrepl-server)
  (start)
  (System/exit 0))


(comment
  (def c (start-conn "bat.org" 23))
  (defn bar []
    (future))
  (Thread/sleep 1000))

