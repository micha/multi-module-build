(ns alpha.core
  (:gen-class)
  (:require [tailrecursion.warp :as warp]))

(defn -main [& argv]
  (warp/try* (println "hello alpha world")))
