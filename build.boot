


(import boot.App)
(require '[boot.pod :as pod]
         '[clojure.java.io :as io])

(deftask pick
  [f files PATH #{str} "The files to pick."
   d dir PATH     str  "The directory to put the files."]
  (with-pre-wrap [fs]
    (with-let [fs fs]
      (let [files (->> (output-files fs)
                       (map (juxt tmp-path tmp-file))
                       (filter #((or files #{}) (first %))))]
        (doseq [[p f] files] (io/copy f (io/file dir p)))))))

(deftask alpha
  []
  (set-env!
    :target-path    "modules/alpha/target"
    :resource-paths #{"modules/alpha/src"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]
                      [tailrecursion/warp  "0.1.0"]])
  (comp (aot :all true)
        (uber)
        (jar :file "alpha.jar" :main 'alpha.core)))

(deftask bravo
  []
  (set-env!
    :target-path    "modules/bravo/target"
    :resource-paths #{"modules/bravo/src" "modules/bravo/resources"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]])
  (comp (aot :all true)
        (uber)
        (jar :file "bravo.jar" :main 'bravo.core)))

(defn runboot
  [& boot-args]
  (future
    (App/runBoot
      (App/newCore)
      (future @pod/worker-pod)
      (into-array String boot-args))))

(deftask build
  []
  (info "Building alpha...\n")
  (runboot "watch" "alpha" "pick" "-d" "modules/bravo/resources" "-f" "alpha.jar")
  (Thread/sleep 5000)
  (info "Building bravo...\n")
  (runboot "watch" "bravo")
  (wait))

