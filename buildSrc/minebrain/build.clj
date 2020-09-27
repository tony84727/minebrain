(ns minebrain.build
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clojure.java.shell :as shell]
            [clojure.core.reducers :as r]))

(def cache-dir ".grpc")

(defn is-exists [path] (.exists (io/file path)))

;;https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.32.1/protoc-gen-grpc-java-1.32.1-osx-x86_64.exe
(def plugin-version "1.32.1")
(def artifact-download-link-base "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java")

(defn artifact-os
  "guess artifact os part from os.name system property"
  [os-name]
  (condp = (string/lower-case (re-find #"(?i)windows|mac|linux" os-name))
    "windows" "windows"
    "mac" "osx"
    "linux" "linux"))

(defn artifact-name [version os arch]
  (str (string/join "-" ["protoc-gen-grpc-java" version (artifact-os os) arch]) ".exe"))

(defn artifact-download-link [version os arch]
  (string/join "/" [artifact-download-link-base version (artifact-name version os arch)]))

(defn download! [link path]
  (io/make-parents path)
  (io/copy (:body (client/get link {:as :stream})) (io/file path)))

(defn os-name [] (System/getProperty "os.name"))
(defn os-arch [] (System/getProperty "os.arch"))

(defn ensure-grpc-plugin-exe!
  []
  (let [os (os-name)
        arch (os-arch)
        local (string/join "/" [cache-dir (artifact-name plugin-version os arch)])]
    (when-not (is-exists local)
      (do (print "downloading plugin ....")
          (download! (artifact-download-link plugin-version os arch) local)
          (.setExecutable (io/file local) true)))))

(defn file-list [dir xf]
  (reduce conj (r/reducer (file-seq (io/file dir)) xf)))

(def file-names (comp
                  (filter #(.isFile %))
                  (map #(.getPath %))))

(defn ls-files [dir]
  (file-list dir file-names))

(defn ext-filter [ext]
  (filter (fn [name] (not-empty (re-matches (re-pattern (str "\\S+\\.\\Q" ext "\\E")) name)))))

(defn ls-files-with-ext [dir ext] (file-list dir (comp file-names (ext-filter ext))))

(defn must-success [& args]
  (let [result (apply shell/sh args)]
    (when-not (= 0 (:exit result)) (throw (ex-info "process exit non-zero" result)))))

(defn generate-grpc!
  []
  (io/make-parents "java/files")
  (must-success "protoc"
            "-Iforge-gRPC/src/main/proto/"
            "--java_out=./java"
            "--grpc-java_out=./java"
            (str "--plugin=protoc-gen-grpc-java=./.grpc/" (artifact-name plugin-version (os-name) (os-arch)))
            (string/join " " (ls-files "forge-gRPC/src/main/proto/"))))
(defn compile-grpc!
  []
  (apply must-success "javac" "-cp" (System/getProperty "java.class.path") (ls-files-with-ext "./java" "java")))
(defn -main [& args]
  (println "Generating & Compling GRPC java files")
  (ensure-grpc-plugin-exe!)
  (generate-grpc!)
  (compile-grpc!)
  (shutdown-agents))

