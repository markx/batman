(ns batman.core
  (:gen-class)
  (:require [batman.conn :as conn]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [batman.readline :as rl]))


(def log-file "debug.log")
(log/merge-config!
 {:appenders
  {:println nil
   :spit (merge (appenders/spit-appender {:fname log-file})
                {:async? true
                 :output-fn
                 (fn [data]
                   (log/default-output-fn (dissoc data :hostname_)))})}})

(defonce triggers (atom []))
(defonce prompt (atom nil))


(defmacro thread [& body]
  `(doto (Thread. (fn [] ~@body))
     (.start)))

(defmacro safe [& body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e#))))


(defn strip-ansi-code [s]
  (clojure.string/replace s #"\x1b\[[0-9;]*[a-zA-Z]" ""))

(defn message [s]
  {:raw (or s "")
   :text (and s (strip-ansi-code s))
   :gag false})


(defn apply-triggers [msg]
  (reduce
   (fn [m f]
     (or (safe
           (f m))
         m))
   msg
   @triggers))

(defn print-message [m]
  (when (and (:raw m)
             (not (:gag m)))
    (rl/print-above-prompt (:raw m)))
  m)

(defn handle-message [m]
  (let [m (apply-triggers m)]
    (if (:prompt m)
      (do
        (rl/update-prompt (:text m))
        (reset! prompt m))
      (print-message m))

    (->> m
        (:text)
        (rl/extract-candidates)
        (rl/add-completion-candidates!))))


(defn handle-frame [{:keys [text prompt] :as f}]
  (if (or text prompt)
    (->> text
      (map char)
      (apply str)
      (message)
      (merge {:prompt prompt})
      (handle-message))
    (log/debug f)))


(defn conn-loop [quit c]
  (let [frames (conn/bytes->frames (conn/read-bytes c))]
    (run! handle-frame frames))

  (deliver quit true))


(defn handle-input [l]
  (log/info "input: " (pr-str l))
  (reset! prompt nil)
  (conn/send-cmd l)
  (println))


(defn- get-input []
  (rl/read-line (:text @prompt)))

(defn input-loop [quit]
  (try
    (loop []
      (when-not (realized? quit)
        (when-let [l (get-input)]
          (handle-input l)
          (recur))))
    (deliver quit true)
    (catch java.net.SocketException e
      (println (.getMessage e)))))


(defn write [f & s]
  (spit f (apply print-str s) :append true))


(defn gag [m]
  (assoc m :gag true))


(defn debug [& args]
  (rl/print-above-prompt (apply println-str (into ["DEBUG: "] args))))

(declare reload-scripts)
(defn inject-utils [ns]
  (doseq [[k f] {'SEND conn/send-cmd
                 'WRITE write
                 'RELOAD-SCRIPTS reload-scripts
                 'DEBUG debug
                 'GAG gag}]
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
      (safe (load-file path))
      (log/info "created ns: " name)

      (when-let [u (resolve (symbol name "UPDATE"))]
        (register-trigger! u)
        (log/info "regiestered UPDATE " (ns-name space))
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
    (thread
      (conn-loop quit c))
    (thread
      (input-loop quit))
    @quit
    (conn/stop-conn)))



(defn -main []
  (start)
  (System/exit 0))


(comment
  (Thread/sleep 1000)
  (load-file "scripts/test.clj")
  (load-script "scripts/test.clj")
  (reload-scripts)
  (let [name (symbol "scripts.aaa")]
    (binding [*ns* (create-ns name)]
      (load-file "scripts/test.clj")))
  (re-find #".*clj$" (.getName (clojure.java.io/file "scripts/test.clj")))
  (run! #(load-file (.getAbsolutePath %)) (filter #(.isFile %) (file-seq (clojure.java.io/file "scripts/")))))

