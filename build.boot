(require
  '[boot.pod :as pod]
  '[clojure.java.io :as io])

(import
  [java.util.concurrent ConcurrentHashMap Semaphore])

(def data (ConcurrentHashMap.))

(deftask pick
  [f files PATH #{str} "The files to pick."
   d dir PATH     str  "The directory to put the files."]
  (with-pre-wrap [fs]
    (with-let [fs fs]
      (let [files (->> (output-files fs)
                       (map (juxt tmp-path tmp-file))
                       (filter #((or files #{}) (first %))))]
        (doseq [[p f] files] (io/copy f (io/file dir p)))))))

(deftask runboot
  [a args ARG [str] "The boot cli arguments."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (future (boot.App/runBoot
                (boot.App/newCore data)
                (future pod/worker-pod)
                (into-array String args))))))

(deftask acquire-permit
  [d data VAR code "The optional data var."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (.putIfAbsent (or data pod/data) "semaphore" (Semaphore. 1 true))
      (.acquire (get (or data pod/data) "semaphore")))))

(deftask release-permit
  [d data VAR code "The optional data var."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (.putIfAbsent (or data pod/data) "semaphore" (Semaphore. 1 true))
      (.release (get (or data pod/data) "semaphore")))))

(deftask say
  [t text TEXT str "The text to print."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (info "%s\n" text))))

(deftask alpha
  []
  (set-env!
    :resource-paths #{"modules/alpha/src"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]
                      [tailrecursion/warp  "0.1.0"]])
  (comp (aot :all true)
        (uber)
        (jar :file "alpha.jar")))

(deftask bravo
  []
  (set-env!
    :resource-paths #{"modules/bravo/src" "modules/bravo/resources"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]])
  (comp (aot :all true)
        (uber)
        (jar :file "bravo.jar")))

(deftask doit
  []
  (comp
    (acquire-permit :data data)
    (runboot :args ["watch" "say" "-t" "alpha" "alpha" "pick" "-f" "alpha.jar" "-d" "modules/bravo/resources" "release-permit"])
    (acquire-permit :data data)
    (runboot :args ["watch" "say" "-t" "bravo" "bravo" "pick" "-f" "bravo.jar" "-d" "target" "release-permit"])
    (wait)
    ))

