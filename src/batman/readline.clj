(ns batman.readline
  (:require [taoensso.timbre :as log])
  (:refer-clojure :exclude [read-line])
  (:import
    [org.jline.terminal TerminalBuilder]
    [org.jline.reader
      LineReader
      LineReader$Option
      LineReaderBuilder
      Completer
      Candidate
      EndOfFileException]))


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


(defn extract-candidates [line]
 (as-> line %
   (clojure.string/replace % #"\w*[@#$%^&*,|]{2,}\w*" " ") ; remove words with weird symbols
   (clojure.string/replace % #"\x1b\[[0-9;]*[a-zA-Z]" " ") ; remove ansi codes
   (clojure.string/replace % #"[^\w\s]" " ")
   (clojure.string/split % #"\s+")
   (remove (comp (partial > 2) count) %)))


(defn style-prompt [prompt]
  ; blue prompt
  (format "\33[34m%s\33[39m" prompt))

(defn read-line
  ([prompt] (read-line default-reader (style-prompt prompt)))
  ([reader prompt]
   (try
     (let [line (.readLine reader prompt)]
       (log/debug "got line" line)
       (->> line
            (extract-candidates)
            (apply add-completion-candidates!))
       line)
     (catch org.jline.reader.EndOfFileException e
       nil))))

(defn print-above-prompt [s]
  (.printAbove default-reader s))
  ;(print "\33[2K\r")

(comment
  ( safe-print "haha\n")
  (re-seq #"^|\s+(\w{2,})\s+|\z" "this&& is  a word")
  (line->words "")
  (read-line))

