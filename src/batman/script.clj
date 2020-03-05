(ns batman.script
  (:require [clojure.string :as string])
  (:require [batman.core :refer [register-alias! register-trigger! handle-code]]))


(defn- transform [p line]
  (let [[f xs] (next (re-find p line))]
    (if (empty? xs)
      (format "(%s)" f)
      (format "(%s \"%s\")" f (apply str xs)))))

(def command-pattern #"/([\w-/]+)((?:\s+\w+)*)\s*$")
(register-alias! command-pattern
                  (fn [line]
                    (batman.core/handle-code (transform command-pattern line)))
                  9999)

(comment
  (re-find command-pattern "/heal/pattern")
  (string/replace "/heal/toggle" command-pattern "($1 \"$2\")"))
