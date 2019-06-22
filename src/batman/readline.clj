(ns batman.readline
  (:require [taoensso.timbre :as log])
  (:refer-clojure :exclude [read-line])
  (:import
    [org.jline.terminal TerminalBuilder]
    [org.jline.keymap
     KeyMap]
    [org.jline.reader
      LineReader
      LineReader$Option
      LineReaderBuilder
      Completer
      Candidate
      EndOfFileException
      UserInterruptException
      Reference]))


(defonce history-dict (atom '()))
(defonce message-dict (atom '()))


(def HISTORY_FILE (str (System/getProperty "user.home") "/.batman_history"))


(defn add-completion-candidates!
 ([coll] (add-completion-candidates! coll message-dict))
 ([coll d]
  (log/debug (prn-str coll))
  (swap! d (comp #(take 5000 %) distinct #(apply conj %1 %2)) coll)))


(defn dict []
  (distinct (into @history-dict @message-dict)))


(defn completer []
  (proxy [Completer] []
    (complete [reader cli candidates]
              (when-let [cs (not-empty (dict))]
                (.addAll candidates
                         (map #(Candidate. %)
                               cs))))))


(defn custom-binding [reader]
  (doto (.get (.getKeyMaps reader) LineReader/MAIN)
     (.bind
       (org.jline.reader.Reference. LineReader/UP_LINE_OR_SEARCH)
       (KeyMap/ctrl \P))
     (.bind
       (org.jline.reader.Reference. LineReader/DOWN_LINE_OR_SEARCH)
       (KeyMap/ctrl \N)))
  reader)


(defn line-reader []
   (let [terminal (-> (TerminalBuilder/builder)
                      (.system true)
                      (.build))
         reader (-> (LineReaderBuilder/builder)
                  (.terminal terminal)
                  (.completer (completer))
                  (.option LineReader$Option/HISTORY_TIMESTAMPED false)
                  (.variables (java.util.HashMap.
                                {LineReader/HISTORY_FILE HISTORY_FILE,
                                 LineReader/HISTORY_SIZE 1000000000,
                                 LineReader/HISTORY_FILE_SIZE 1000000000,}))
                  (.build))]
    (custom-binding reader)
    reader))

(def default-reader (line-reader))


(defn extract-candidates [line]
 (as-> line %
   (clojure.string/replace % #"\x1b\[[0-9;]*[a-zA-Z]" "") ; remove ansi codes
   (clojure.string/replace % #"\w*[@#$%^&*,|_-]{2,}\w*" " ") ; remove words with weird symbols
   (clojure.string/replace % #"[^\w\s-]" " ")
   (clojure.string/split % #"\s+")
   (remove (comp (partial > 2) count) %)))


(defn style-prompt [prompt]
  ; blue prompt
  (format "\33[34m%s\33[39m" (or prompt "")))

(defn print-above-prompt [s]
  (.printAbove default-reader s))

(defn read-line
  ([prompt] (read-line default-reader (style-prompt prompt) false))
  ([reader prompt quit]
   (try
     (let [line (.readLine reader prompt)] ; TODO: cancel this?
       (log/debug "got line" line)
       (-> line
            (extract-candidates)
            (add-completion-candidates! history-dict))
       line)
     (catch EndOfFileException e
       nil)
     (catch UserInterruptException e
       (when-not quit
         (print-above-prompt "Press ctrl-c again to quit.")
         (read-line default-reader prompt true))))))


(defn update-prompt [prompt]
  (doto default-reader
    (.setPrompt (style-prompt prompt))
    (.redisplay)))


(comment
  (safe-print "haha\n")
  (clojure.string/replace "this&& is 15 words" #"\w*[@#$%^&*,]{2,}\w*" " ")
  (clojure.string/replace "\33[34mhahahahhaha\33[39m" #"\x1b\[[0-9;]*[mG]" " ")
  (extract-candidates "this&& is  a word")
  (extract-candidates "")
  (read-line)
  (.bind (.get (.getKeyMaps default-reader) LineReader/MAIN)
     (org.jline.reader.Reference. LineReader/UP_LINE_OR_SEARCH)
     (KeyMap/ctrl \P)))

