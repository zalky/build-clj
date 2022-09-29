(ns io.zalky.build
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.write-pom :as bpom]
            [clojure.tools.build.util.file :as file]
            [clojure.walk :as walk]
            [org.corfield.build :as cb])
  (:import java.io.File
           java.net.URI
           [java.nio.file Files FileSystem FileSystems StandardCopyOption]
           java.nio.file.attribute.FileAttribute
           java.util.HashMap))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn root-file
  []
  (-> "user.dir"
      (System/getProperty)
      (file/ensure-dir)))

(defn filter-meta-files
  [{re  :meta-inf-files
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

(defn meta-inf-file
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
      (doseq [^File f (.listFiles root)]
        (when-let [mif (some->> (.toPath f)
                                (.relativize root-p)
                                (.toString)
                                (filter-meta-files opts)
                                (meta-inf-file opts))]
          (add-to-jar fs f mif)))))
  opts)

(def licenses
  {:epl-1  {:name "Eclipse Public License 1.0"
            :url  "https://www.eclipse.org/legal/epl-v10.html"}
   :epl-2  {:name "Eclipse Public License 2.0"
            :url  "https://www.eclipse.org/legal/epl-v20.html"}
   :apache {:name "Apache License Version 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"}
   :mit    {:name "The MIT License"
            :url  "https://opensource.org/licenses/MIT"}})

(defn license-el
  [{:keys [license]}]
  (when-let [{:keys [name url]} (get licenses license)]
    (xml/sexp-as-element
     [::pom/licenses
      [::pom/license
       [::pom/name name]
       [::pom/url url]]])))

(defn desc-el
  [{:keys [description]}]
  (when description
    (xml/sexp-as-element
     [::pom/description description])))

(defn append-xml
  [pom el]
  (cond-> pom
    el (update :content concat [el])))

(defn pom-dir
  [{:keys [class-dir target lib]}]
  (file/ensure-dir
   (cond
     class-dir (-> class-dir
                   (b/resolve-path)
                   (io/file (bpom/meta-maven-path {:lib lib})))
     target    (-> target
                   (b/resolve-path)
                   (io/file)
                   (file/ensure-dir)))))

(defn class-dir-pom-file
  [opts]
  (let [file (io/file (pom-dir opts) "pom.xml")]
    (if-not (.exists file)
      (throw
       (ex-info
        "No pom.xml in :class-dir or :target."
        {:path (.getPath file)}))
      file)))

(defn- realize-all
  [x]
  (walk/postwalk identity x))

(defn read-pom
  "Must realize the fully nested pom before we close the reader and fall
  out of scope. We need to close the reader before we open it
  again for writing."
  [file]
  (with-open [r (io/reader file)]
    (-> r
        (xml/parse :skip-whitespace true)
        (realize-all))))

(defn write-pom
  [pom file]
  (with-open [w (io/writer file)]
    (xml/indent pom w)))

(defn jar-pom-file
  [{:keys [class-dir]} pom-file]
  (-> (io/file class-dir)
      (.toPath)
      (.relativize (.toPath pom-file))
      (str)
      (io/file)))

(defn add-pom-attributes
  [opts]
  (let [file (class-dir-pom-file opts)]
    (with-open [fs (jar-file-system opts)]
      (-> file
          (read-pom)
          (append-xml (license-el opts))
          (append-xml (desc-el opts))
          (write-pom file))
      (->> file
           (jar-pom-file opts)
           (add-to-jar fs file))))
  opts)

(defn jar
  "Same semantics as org.corfield.build/jar, but adds files in the
  project root to the jar META-INF folder via regex, and license and
  description elements to the pom.xml.

  Options:
  :meta-inf-files  - Seq of regex patterns to match against files
                     in the project root. By default (?i)license
                     (?i)readme. Matched files will be included in
                     META-INF folder of the jar.
  :license         - See the keys of io.zalky.build/licesnse for
                     valid licenses
  :description     - Project description"
  [opts]
  (-> opts
      (#'cb/jar-opts)
      (cb/jar)
      (add-pom-attributes)
      (jar-add-meta-files))
  opts)

(def clean cb/clean)
(def uber cb/uber)
(def install cb/install)
(def deploy cb/deploy)
(def run-task cb/run-task)
(def run-test cb/run-tests)
