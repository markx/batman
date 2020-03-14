(ns batman.main
  (:gen-class)
  (:require [batman.core :as core]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]))



(def cli-options
  ;; An option with a required argument
  [["-H" "--host HOST" "Server host"
    :default "bat.org"]
   ["-P" "--port PORT" "Port number"
    :default 23
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-s" "--scripts PATH" "Path to scripts"
    :default ""
    :validate [#(.exists (clojure.java.io/file %)) "Path doesn't exist"]]
   ;; A non-idempotent option (:default is applied first)
   ["-h" "--help"]])


(defn usage [options-summary]
  (->> ["Batman, a client for MUDs, like Batmud."
        ""
        "Usage: batman [options]"
        ""
        "Options:"
        options-summary]

       (string/join \newline)))

(defn error-msg [errors]
  (string/join \newline errors))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      :else
      {:options options})))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn transform-options
  [{:keys [scripts] :as opts}]
  (merge {:scripts-path scripts} opts))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (core/start (transform-options options))))
  (System/exit 0))

(comment)
