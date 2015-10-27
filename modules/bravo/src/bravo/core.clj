(ns bravo.core
  (:gen-class)
  (:require [clojure.java.io :as io]))

(defn -main [& argv]
  (when-not (io/resource "alpha.jar")
    (throw (Exception. "no alpha.jar on classpath")))
  (println "hello bravo world"))
