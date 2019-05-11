(ns batman.readline
  (:require [taoensso.timbre :as log])
  (:import 
    [org.jline.reader LineReaderBuilder Completer Candidate]
    [org.jline.terminal TerminalBuilder]))


(defonce dict (atom '()))



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
        (.build))))
  
(def default-reader (line-reader))


(defn- line->words [line]
 (as-> line %
   (clojure.string/split % #"\s+")
   (remove empty? %)))

 
(defn read-line 
  ([prompt] (read-line default-reader prompt))
  ([reader prompt]
   (log/debug "prompt:" prompt)
   (let [line (.readLine reader prompt)]
     (->> line
          (line->words)    
          (apply add-completion-candidates!))
     line)))



(comment 
  (line->words "")
  (read-line))

