(require
  '[boot.pod :as pod]
  '[clojure.java.io :as io])

(import [java.util.concurrent ConcurrentHashMap Semaphore])

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask say
  [t text TEXT str "The text to print."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (info "%s\n" text))))

(deftask pick
  [f files PATH #{str} "The files to pick."
   d dir PATH     str  "The directory to put the files."]
  (with-pre-wrap [fs]
    (with-let [fs fs]
      (let [files (->> (output-files fs)
                       (map (juxt tmp-path tmp-file))
                       (filter #((or files #{}) (first %))))]
        (doseq [[p f] files] (io/copy f (io/file dir p)))))))

(deftask release-permit
  [d data VAR code "The optional data var."]
  (with-pre-wrap [fs]
    (with-let [_ fs]
      (.putIfAbsent (or data pod/data) "semaphore" (Semaphore. 1 true))
      (.release (get (or data pod/data) "semaphore")))))

(deftask runboot
  [d data SYM code  "The global state Map."
   a args ARG [str] "The boot cli arguments."]
  (assert data "You must provide a ConcurrentHashMap as --data.")
  (let [core   (boot.App/newCore data)
        worker (future pod/worker-pod)
        args   (into-array String ((fnil conj []) args "release-permit"))]
    (with-pre-wrap [fs]
      (with-let [_ fs]
        (.putIfAbsent data "semaphore" (Semaphore. 1 true))
        (.acquire (get data "semaphore"))
        (future (boot.App/runBoot core worker args))))))

;; module build tasks ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; multi-module build task ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask doit
  []
  (let [data (ConcurrentHashMap.)]
    (comp (runboot :data data :args ["watch" "say" "-t" "alpha" "alpha" "pick" "-f" "alpha.jar" "-d" "modules/bravo/resources"])
          (runboot :data data :args ["watch" "say" "-t" "bravo" "bravo" "pick" "-f" "bravo.jar" "-d" "target"])
          (wait))))

