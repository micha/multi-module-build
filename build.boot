(require
  '[boot.pod :as pod]
  '[clojure.java.io :as io])

(import [java.util.concurrent ConcurrentHashMap Semaphore])

(def ^:dynamic *boot-data* nil)

(defmacro with-boot-context
  [& body]
  `(binding [boot.user/*boot-data* (ConcurrentHashMap.)] ~@body))

(defmacro with-pass-thru
  [bindings & body]
  (let [bindings (if (vector? bindings) (first bindings) bindings)]
    `(boot.core/with-pre-wrap [fs#]
       (boot.util/with-let [~bindings fs#] ~@body))))

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask say
  [t text TEXT str "The text to print."]
  (with-pass-thru [fs]
    (info "%s\n" text)))

(deftask pick
  [f files PATH #{str} "The files to pick."
   d dir PATH     str  "The directory to put the files."]
  (with-pass-thru [fs]
    (let [files (->> (output-files fs)
                     (map (juxt tmp-path tmp-file))
                     (filter #((or files #{}) (first %))))]
      (doseq [[p f] files] (io/copy f (io/file dir p))))))

(deftask release-permit
  []
  (with-pass-thru [fs]
    (.release (get pod/data "semaphore"))))

(deftask runboot
  [a args ARG [str] "The boot cli arguments."]
  (let [data   *boot-data*
        core   (boot.App/newCore data)
        worker (future pod/worker-pod)
        args   (-> (vec (remove empty? args))
                   (conj "release-permit")
                   (->> (into-array String)))]
    (.putIfAbsent data "semaphore" (Semaphore. 1 true))
    (with-pass-thru [fs]
      (.acquire (get data "semaphore"))
      (future (boot.App/runBoot core worker args)))))

;; module build tasks ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask alpha
  []
  (set-env!
    :resource-paths #{"modules/alpha/src"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]
                      [tailrecursion/warp  "0.1.0"]])
  (comp (say :text "Building alpha...")
        (aot :all true)
        (uber)
        (jar :file "alpha.jar")))

(deftask bravo
  []
  (set-env!
    :resource-paths #{"modules/bravo/src" "modules/bravo/resources"}
    :dependencies   '[[org.clojure/clojure "1.7.0"]])
  (comp (say :text "Building bravo...")
        (aot :all true)
        (uber)
        (jar :file "bravo.jar")))

;; multi-module build task ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask doit
  []
  (with-boot-context
    (comp (runboot :args ["watch" "alpha" "pick" "-f" "alpha.jar" "-d" "modules/bravo/resources"])
          (runboot :args ["watch" "bravo" "pick" "-f" "bravo.jar" "-d" "target"])
          (wait))))

