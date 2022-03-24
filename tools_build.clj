(ns tools-build
  (:require [clojure.tools.build.api :as b]))

(def lib 'lasvice/lan-show)
(def version "0.1.0")
(def class-dir "classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "%s-%s.jar" (name lib) version))
(def uber-file (format "%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "classes"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources" "target"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources" "target"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'lasvice.backend.server}))
