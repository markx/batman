(ns batman.conn
  (:require [taoensso.timbre :as log]
            [clj-telnet.core :as telnet])
  (:import
    [java.net Socket]
    [java.io BufferedInputStream]
    [java.io PrintStream PrintWriter]))


(def cmd-SE    240)
(def cmd-NOP   241)
(def cmd-DM    242)
(def cmd-BRK   243)
(def cmd-IP    244)
(def cmd-AO    245)
(def cmd-AYT   246)
(def cmd-EC    247)
(def cmd-EL    248)
(def cmd-GA    249)
(def cmd-SB    250)
(def cmd-WILL  251)
(def cmd-WONT  252)
(def cmd-DO    253)
(def cmd-DONT  254)
(def cmd-IAC   255)

(defonce conn (atom nil))

(defn client [host port]
  (let [s (Socket. host port)
        in (BufferedInputStream. (.getInputStream s))
        out (.getOutputStream s)]
    s))

(defn write [c s]
  (let [out (PrintStream. (.getOutputStream c))]
    (doto out
      (.println s))))

(defn start-conn [host port]
  (reset! conn (client host port)))

(defn stop-conn []
  (when @conn
    (.close @conn)
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

(defn send-cmd [s]
  (write @conn s))


(defn- take-byte-or-IAC [s]
  (when (seq s)
    (if (= cmd-IAC (first s) (second s))
      {:b (first s)
       :rest (rest (rest s))}

      {:b (first s)
       :rest (rest s)})))

(defn- cmd-seq? [s]
  (and (= cmd-IAC (first s))
       (not= cmd-IAC (second s))))


(defn take-text [s]
  (when (seq s)
    (let [x (first s)
          rs (rest s)]
      (cond
        (= (byte \newline) x)
        {:frame {:text [\newline]}
         :rest rs}

        (and (= cmd-IAC x)
             (= cmd-GA (first rs)))
        {:frame {:text nil :prompt true}
         :rest (rest rs)}

        (cmd-seq? s)
        {:frame nil
         :rest s}

        ; maybe unnecessary? 255 is meaningless in text?
        (= cmd-IAC x (first rs))
        (let [{subframe :frame rs :rest} (take-text (rest rs))]
          {:frame (update subframe :text (partial cons x))
           :rest rs})

        :else
        (let [{subframe :frame rs :rest} (take-text rs)]
          {:frame (update subframe :text (partial cons x))
           :rest rs})))))

(defn take-cmd [s]
  (when (= (first s) cmd-IAC)
    (let [x (second s)
          rs (rest (rest s))]
      (cond
        (or
          (= cmd-DO x)
          (= cmd-DONT x)
          (= cmd-WILL x)
          (= cmd-WONT x))
        (let [{:keys [b rest]} (take-byte-or-IAC rs)]
          {:frame {:cmd [x b]}
           :rest rest})

        (= cmd-SB x)
        (loop [result nil rs rs]
          (if (and (= cmd-IAC (first rs))
                  (= cmd-SE (second rs)))
              {:frame {:cmd result}
               :rest (rest (rest rs))}
              (let [{:keys [b rest]} (take-byte-or-IAC rs)]
                (recur (conj result b) rest))))

        :else
        (do
          (log/info "unknown cmd" x)
          {:frame nil
           :rest rs})))))

(defn handle-seq [s]
  (when (seq s)
    (let [take-func (if (cmd-seq? s)
                        take-cmd
                        take-text)]
      (let [{:keys [frame rest]} (take-func s)]
        (log/debug "!!!frame: " frame)
        (if frame
          (cons frame (lazy-seq (handle-seq rest)))
          (handle-seq rest))))))

(defn stream->bytes [in]
  (take-while some?
   (repeatedly
     (fn []
       (try
         (let [b (.read in)]
           (if (= b -1) ;EOF
             (throw (java.io.EOFException. "EOF"))
             b))
         (catch java.io.IOException e
           (println (.getMessage e)))
         (catch Exception e
           (prn e)))))))


(defn read-bytes [socket]
  (-> socket
      (.getInputStream)
      (stream->bytes)))



(comment
  (take-text [255 255 \a])
  (let [x 255
        rs [255]]
    (cond
          cmd-IAC 0
          \newline 1
          "default"))
  (def s [96 \a 10 13 \a \a cmd-IAC cmd-DO \b 255 255 \a 255 249 cmd-IAC cmd-SB \a \a \a cmd-IAC cmd-SE \a \a])
  (handle-seq s)

  (def in (.getInputStream (start-conn "bat.org" 23)))
  (def s (stream->bytes (.getInputStream (start-conn "bat.org" 23))))
  (take-cmd [cmd-IAC cmd-SB 123 123 123 cmd-IAC cmd-SE 123 123])
  (take 10 (stream->bytes in))

  (take-cmd (seq [255 cmd-DO 22 \a])))

