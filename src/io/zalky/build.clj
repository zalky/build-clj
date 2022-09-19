(ns io.zalky.build
  (:require [clojure.java.io :as io]
            [clojure.tools.build.util.file :as file]
            [org.corfield.build :as cb])
  (:import java.io.File
           java.net.URI
           [java.nio.file Files FileSystem FileSystems StandardCopyOption]
           java.nio.file.attribute.FileAttribute
           java.util.HashMap))

(defn root-file
  []
  (-> "user.dir"
      (System/getProperty)
      (file/ensure-dir)))

(defn filter-meta-files
  [{re  :meta-file-re
    :or {re ["(?i)license"
             "(?i)readme"]}} s]
  (some #(when (re-find (re-pattern %) s) s) re))

(defn jar-root-uri
  [opts]
  (->> (:jar-file opts)
       (io/file)
       (.getCanonicalPath)
       (format "jar:file:%s")
       (URI/create)))

(defn jar-path
  [^FileSystem fs ^File file]
  (->> (into-array String [])
       (.getPath fs (str file))))

(defn jar-ensure-parents
  [^FileSystem fs ^File file]
  (when-let [p (some->> file
                        (.getParent)
                        (jar-path fs))]
    (->> (into-array FileAttribute [])
         (Files/createDirectories p))))

(defn add-to-jar
  [^FileSystem fs ^File from ^File to]
  (let [p1 (.toPath from)
        p2 (jar-path fs to)]
    (jar-ensure-parents fs to)
    (->> [StandardCopyOption/REPLACE_EXISTING]
         (into-array StandardCopyOption)
         (Files/copy p1 p2))))

(defn ^FileSystem jar-file-system
  [opts]
  (let [uri    (jar-root-uri opts)
        config (doto (HashMap.)
                 (.put "create" "true"))]
    (FileSystems/newFileSystem uri config)))

(defn meta-inf-path
  [{lib :lib} s]
  (io/file "META-INF"
           "build-clj"
           (namespace lib)
           (name lib)
           s))

(defn jar-add-meta-files
  [opts]
  (let [root   (root-file)
        root-p (.toPath root)]
    (with-open [fs (jar-file-system opts)]
      (doseq [^File f (file/collect-files root)]
        (when-let [p (some->> (.toPath f)
                              (.relativize root-p)
                              (.toString)
                              (filter-meta-files opts)
                              (meta-inf-path opts))]
          (add-to-jar fs f p))))))

(defn jar
  [opts]
  (-> opts
      (#'cb/jar-opts)
      (cb/jar)
      (jar-add-meta-files))
  opts)

(def clean cb/clean)
(def uber cb/uber)
(def install cb/install)
(def deploy cb/deploy)
(def run-task cb/run-task)
(def run-test cb/run-tests)
