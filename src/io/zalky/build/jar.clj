(ns io.zalky.build.jar
  (:require [clojure.java.io :as io]
            [clojure.tools.build.util.file :as file])
  (:import (java.io File)
           (java.net URI)
           (java.nio.file Files FileSystem FileSystems StandardCopyOption)
           (java.nio.file.attribute FileAttribute)
           (java.util HashMap)))

(defn user-dir
  []
  (-> "user.dir"
      (System/getProperty)
      (file/ensure-dir)))

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

(defn meta-inf-filter
  [{re  :meta-inf-files
    :or {re ["(?i)license"
             "(?i)readme"]}} s]
  (some #(when (re-find (re-pattern %) s) s) re))

(defn meta-inf-file
  [{lib :lib} s]
  (io/file "META-INF"
           "build-clj"
           (namespace lib)
           (name lib)
           s))

(defn jar-add-meta-files
  [opts]
  (let [root   (user-dir)
        root-p (.toPath root)]
    (with-open [fs (jar-file-system opts)]
      (doseq [^File f (.listFiles root)]
        (when-let [mif (some->> (.toPath f)
                                (.relativize root-p)
                                (.toString)
                                (meta-inf-filter opts)
                                (meta-inf-file opts))]
          (add-to-jar fs f mif)))))
  opts)
