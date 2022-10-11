(ns io.zalky.build
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.write-pom :as bpom]
            [clojure.tools.build.util.file :as file]
            [clojure.zip :as z]
            [deps-deploy.deps-deploy :as deploy]
            [io.zalky.build.jar :as jar]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

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

(defn url-el
  [{:keys [url]}]
  (when url
    (xml/sexp-as-element
     [::pom/url url])))

(defn append-xml
  [pom el]
  (cond-> pom
    el (update :content concat [el])))

(defn pom-dir
  [{:keys [class-dir lib]}]
  (file/ensure-dir
   (-> class-dir
       (b/resolve-path)
       (io/file (bpom/meta-maven-path {:lib lib})))))

(defn class-dir-pom-file
  [opts]
  (let [file (io/file (pom-dir opts) "pom.xml")]
    (if-not (.exists file)
      (throw
       (ex-info
        "No pom.xml in :class-dir"
        {:path (.getPath file)}))
      file)))

(defn- xml-element
  [{:keys [tag attrs]
    :as   node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))

(defn- xml-zipper
  [root]
  (z/zipper xml/element? :content xml-element root))

(defn- realize-all
  "Must preserve xml metadata, cannot use clojure.walk."
  [root]
  (let [z (xml-zipper root)]
    (loop [n z]
      (if-not (z/end? n)
        (recur (z/next n))
        (z/root n)))))

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
    (with-open [fs (jar/jar-file-system opts)]
      (-> file
          (read-pom)
          (append-xml (license-el opts))
          (append-xml (desc-el opts))
          (append-xml (url-el opts))
          (write-pom file))
      (->> file
           (jar-pom-file opts)
           (jar/add-to-jar fs file))))
  opts)

(defn crumbs
  [& xs]
  (str (apply io/file xs)))

(defn jar-target-file
  [target lib version]
  (->> version
       (format "%s-%s.jar" (name lib))
       (crumbs target)))

(defn jar-params
  [opts]
  (let [lib    (or (opts :lib)       (throw (Exception. ":lib required")))
        v      (or (opts :version)   (throw (Exception. ":version required")))
        j-dir  (or (opts :jar-dir)   "target")
        basis  (-> (opts :basis)     (b/create-basis))
        s-dirs (or (opts :src-dirs)  (:paths basis))
        c-dir  (or (opts :class-dir) (crumbs j-dir "classes"))
        j-file (jar-target-file j-dir lib v)]
    (->> {:target    j-dir
          :class-dir c-dir
          :src-dirs  s-dirs
          :basis     basis
          :jar-file  j-file
          :uber-file j-file}
         (merge opts))))

(defn uber-params
  [opts]
  (let [main (or (opts :main) (throw (Exception. ":main required")))]
    (-> opts
        (jar-params)
        (assoc :ns-compile [main]))))

(defn copy-for-jar
  [{:keys [src-dirs class-dir]
    :as   opts}]
  (b/copy-dir
   {:src-dirs   src-dirs
    :target-dir class-dir})
  opts)

(defn jar
  "Creates a library jar.

  Required:

  :lib
            Release group id and artifact id

  :version
            Release version

  Optional:

  :jar-dir
            Directory of output jar. Default is target

  :basis
            Options for clojure.tools.build/create-basis subtasks

  :class-dir
            Intermediary directory where contents of the jar are
            collected before archiving. Default is <:jar-dir>/classes/

  :src-dirs
            An explicit list of source directories to include in the
            jar. If not specified, everything on the classpath will be
            included.

  :meta-inf-files
            Seq of regex patterns to match against files in the
            project root, by default `(?i)license` and
            `(?i)readme`. Matched files will be included in the
            META-INF folder of the jar.

  :license
            One of the valid licenses enumerated by the keys of
            io.zalky.build/licenses. The attributes of this license
            will be added to the pom.xml file. This license should
            also match the file that is included via the
            :meta-inf-files regex patterns.

  :description
            Project description added to pom.xml

  :url
            Project url added to pom.xml"
  [opts]
  (let [params (jar-params opts)]
    (b/write-pom params)
    (copy-for-jar params)
    (b/jar params)
    (-> params
        (add-pom-attributes)
        (jar/jar-add-meta-files)))
  opts)

(defn uber
  "Creates a library jar.

  Required:

  :lib
            Release group id and artifact id

  :version
            Release version

  :main
            Namespace with -main function. Namespace must be
            configured with :gen-class.

  Additional options, if provided, are identical to
  io.zalky.build/jar."
  [opts]
  (let [params (uber-params opts)]
    (b/write-pom params)
    (copy-for-jar params)
    (b/compile-clj params)
    (b/uber params)
    (-> params
        (add-pom-attributes)
        (jar/jar-add-meta-files)))
  opts)

(defn install
  "Install a jar to the local Maven repo.

  Required:

  :lib
            Release group id and artifact id

  :version
            Release version

  Required if the jar is not in target directory:

  :jar-dir
            Location of jar file

  Additional options, if provided, are identical to
  io.zalky.build/jar."
  [opts]
  (-> opts
      (jar-params)
      (b/install))
  opts)

(defn deploy
  "Deploys a jar to a repository.

  Required:

  :lib
            Release group id and artifact id

  :version
            Release version

  Required if the jar is not in target directory:

  :jar-dir
            Location of jar file

  Additional options conform to the semantics of the
  deps-deploy.deps-deploy/deploy fn:

  :repository
            If left unspecified, Clojars is assumed

  :sign-releases?
            Default true

  :sign-key-id
            The gpg signing key to use. If left unspecified, default
            key is used."
  [opts]
  (let [params (jar-params opts)]
    (->> {:installer :remote
          :artifact  (:jar-file params)
          :pom-file  (class-dir-pom-file params)}
         (merge opts)
         (deploy/deploy)))
  opts)
