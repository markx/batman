(ns batman.core
  (:gen-class)
  (:require [batman.conn :as conn]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [batman.readline :as rl]
            [batman.script :as script]))


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
   (map :func (sort-by :priority @triggers))))


(defn is-code [l]
  (not-empty (re-find #"^\(.+\)$" l)))

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


(defn handle-code [l]
  (binding [*ns* (find-ns 'batman.script)]
    (safe (prn (load-string l)))))


(defn handle-input [l]
  (log/info "input: " (pr-str l))
  (if (is-code l)
    (handle-code l)
    (do
      (reset! prompt nil)
      (conn/send-cmd l))))
  ;(println))


(defn- get-input []
  (rl/read-line (:text @prompt)))

(defn input-loop [quit]
  (while (not (realized? quit))
    (try
      (if-let [l (get-input)]
        (handle-input l)
        (deliver quit true))
      (catch java.net.SocketException e
        (println (.getMessage e))
        (deliver quit true))
      (catch Throwable e
        (prn e)))))


(defn write [f & s]
  (spit f (apply print-str s) :append true))


(defn gag [m]
  (assoc m :gag true))


(defn debug [& args]
  (rl/print-above-prompt (apply println-str (into ["DEBUG: "] args))))


(defn register-trigger!
  ([f] (register-trigger! f 1))
  ([f priority]
   (swap! triggers conj {:func f
                         :priority priority})
   (log/info "regiestered" f priority)
   (log/info "triggers" @triggers)))


(declare reload-scripts)
(defn inject-utils [ns]
  (doseq [[k f] {'SEND conn/send-cmd
                 'WRITE write
                 'RELOAD-SCRIPTS reload-scripts
                 'DEBUG debug
                 'GAG gag
                 'TRIGGER register-trigger!}]
    (intern ns k f)))


(defn load-script [file]
  (let [fname (-> file 
                  (.getName)
                  (clojure.string/replace #"\.clj$" ""))
        nsname (str "script." fname)
        space (create-ns (symbol nsname))]
    (inject-utils space)
    (binding [*ns* space]
      (clojure.core/refer-clojure)
      (safe (load-file (.getPath file)))
      (log/info "created ns: " nsname))

    (binding [*ns* (find-ns 'batman.script)]
      (log/debug (symbol fname) nsname)
      (alias (symbol fname) (symbol nsname)))))



(defn load-scripts
  ([] (load-scripts "scripts/"))
  ([path] (run! load-script
                (filter (fn [file]
                          (and (.isFile file)
                               (re-find #"\.clj$" (.getName file))))
                        (file-seq (clojure.java.io/file path))))))


(defn reload-scripts []
  (reset! triggers [])
  (load-scripts))

(def default-opts {:host "bat.org"
                   :port 23})

(defn start
  ([] (start nil))
  ([opts]
   (let [{:keys [host port]} (merge default-opts opts)
                quit (promise)
                c (conn/start-conn host port)]
     (log/info "connected to server" c)
     (reload-scripts)
     (thread
       (conn-loop quit c))
     (thread
       (input-loop quit))
     @quit
     (conn/stop-conn))))


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

