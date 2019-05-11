(ns batman.readline
  (:require [taoensso.timbre :as log])
  (:import
    [org.jline.terminal TerminalBuilder]
    [org.jline.reader
      LineReader
      LineReader$Option
      LineReaderBuilder
      Completer
      Candidate]))


(defonce dict (atom '()))


(def HISTORY_FILE (str (System/getProperty "user.home") "/.batman_history"))


(defn add-completion-candidates! [& s]
 (swap! dict concat s))

(defn completer []
 (proxy [Completer] []
   (complete [reader cli candidates]
             (when (not-empty @dict)
               (.addAll candidates
                        (map #(Candidate. %)
                             @dict))))))

(defn line-reader []
   (let [terminal (-> (TerminalBuilder/builder)
                      (.system true)
                      (.build))]
    (-> (LineReaderBuilder/builder)
        (.terminal terminal)
        (.completer (completer))
        (.option LineReader$Option/HISTORY_TIMESTAMPED false)
        (.variables (java.util.HashMap. {LineReader/HISTORY_FILE HISTORY_FILE}))
        (.build))))

(def default-reader (line-reader))


(defn- line->words [line]
 (as-> line %
   (clojure.string/split % #"\s+")
   (remove empty? %)))


(defn read-line
  ([prompt] (read-line default-reader prompt))
  ([reader prompt]
   (let [line (.readLine reader prompt)]
     (log/debug "got line" line)
     (->> line
          (line->words)
          (apply add-completion-candidates!))
     line)))



(comment
  (line->words "")
  (read-line))

