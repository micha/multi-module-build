(require
  '[boot.pod :as pod]
  '[clojure.java.io :as io])

;; possible new boot built-in task -- runboot ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import [java.util.concurrent ConcurrentHashMap Semaphore])

(def ^:dynamic *boot-data* nil)

(defmacro with-boot-context
  [& body]
  `(binding [boot.user/*boot-data* (ConcurrentHashMap.)] ~@body))

(deftask release-permit []
  (with-pass-thru [fs] (.release (get pod/data "semaphore"))))

(deftask runboot
  "Run boot in boot."
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

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask say
  "Task that prints something."
  [t text TEXT str "The text to print."]
  (with-pass-thru [fs] (info "%s\n" text)))

;; module build tasks ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftask alpha
  "Builds the alpha module."
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
  "Builds the bravo module."
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
  "Builds alpha and bravo modules, with incremental compilation."
  []
  (with-boot-context
    (comp (runboot :args ["watch" "alpha" "sift" "-i" "^alpha\\.jar$" "target" "-d" "modules/bravo/resources"])
          (runboot :args ["watch" "bravo" "sift" "-i" "^bravo\\.jar$" "target" "-d" "target"])
          (wait))))

