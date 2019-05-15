(ns batman.core
  (:gen-class)
  (:require [batman.conn :as conn]
            [nrepl.cmdline :as nrepl-cmdline]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))


(defn prompt-match [m]
  (let [p #"^Hp:(\d+)/(\d+) Sp:(\d+)/(\d+) Ep:(\d+)/(\d+) Exp:(\d+) >"]
    (re-seq p (:message m))))

(def log-file "debug.log")
(log/merge-config!
 {:appenders
  {:println nil
   :spit (merge (appenders/spit-appender {:fname log-file }) {:async? true})}})

(defonce triggers (atom []))

(defonce prompt (atom nil))
(defonce prompt-future (atom nil))



(defn potential-prompt? [m]
  (not= \newline (last (:message m))))

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
    (if @prompt
      (do
        (print "\33[2K\r")
        (print (:message m))
        (print (:message @prompt)))
      (print (:message m)))
    (flush))
  m)

(defn handle-message [m]
  (let [m (apply-triggers m)]
    (print-message m)
    (when (potential-prompt? m)
      (when (future? @prompt-future)
        (future-cancel @prompt-future))
      (if (prompt-match m)
        (reset! prompt m)
        (reset! prompt-future
                (future
                  (Thread/sleep 400)
                  (reset! prompt m)
                  (log/debug "update prompt" (prn-str (:message @prompt)))))))))


(defn handle-frames [fs]
  (->> fs
       (map :text)
       (remove nil?)
       (map (partial map char))
       (map (partial reduce str))))


(defn conn-loop [quit c]
  (let [frames (conn/handle-seq (conn/read-bytes c))
        lines (handle-frames frames)]
    (run! (fn [l]
            (-> l
                (message)
                (handle-message)))
          lines))

  (deliver quit true))


(defn handle-input [c l]
  (log/info "input: " (pr-str l))
  (conn/write c l)
  (reset! prompt nil)
  (reset! prompt-future nil)
  (println))

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


(defn gag [m]
  (assoc m :gag true))

(declare reloads-scripts)
(defn inject-utils [ns]
  (doseq [[k f] {'send-cmd conn/send-cmd
                 'write-to write-to
                 'reloads-scripts reloads-scripts
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
  (let [quit (promise)
        c (conn/start-conn "bat.org" 23)]
    (log/info "connected to server" c)
    (load-scripts)
    (future
      (input-loop quit c))
    (future
      (conn-loop quit c))
    @quit
    (conn/stop-conn)))


(defn -main []
  (start-nrepl-server)
  (loop []
    (start)
    (println "restarting app")
    (recur))
  (System/exit 0))


(comment
  (Thread/sleep 1000)
  (load-file "scripts/test.clj")
  (load-script "scripts/test.clj")
  (reload-scripts)
  (let [name (symbol "scripts.aaa")]
    (binding [*ns* (create-ns name)]
      (load-file "scripts/test.clj")))
  (write-to "chat.txt" "hahahha" "heehehhe")
  (re-find #".*clj$" (.getName (clojure.java.io/file "scripts/test.clj")))
  (run! #(load-file (.getAbsolutePath %)) (filter #(.isFile %) (file-seq (clojure.java.io/file "scripts/")))))


