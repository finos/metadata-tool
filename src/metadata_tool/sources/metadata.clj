;
; Copyright © 2017 FINOS Foundation
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
(ns metadata-tool.sources.metadata
  (:require [clojure.string                :as s]
            [clojure.tools.logging         :as log]
            [clojure.java.io               :as io]
            [mount.core                    :as mnt :refer [defstate]]
            [cheshire.core                 :as ch]
            [metadata-tool.config          :as cfg]
            [metadata-tool.sources.github  :as gh]
            [metadata-tool.sources.schemas :as sch]))

(defstate ^:private organization-metadata-directory :start (str gh/metadata-directory "/organizations"))
(defstate ^:private people-metadata-directory       :start (str gh/metadata-directory "/people"))
(defstate ^:private program-metadata-directory      :start (str gh/metadata-directory "/programs"))

(def ^:private organization-filename "organization-metadata.json")
(def ^:private person-filename       "person-metadata.json")
(def ^:private program-filename      "program-metadata.json")
(def ^:private project-filename      "project-metadata.json")
(def ^:private repository-filename   "repository-metadata.json")

(defn- list-metadata-files
  [filename]
  (doall
    (sort-by #(.getCanonicalPath ^java.io.File %)
             (filter #(= filename (.getName ^java.io.File %)) (file-seq (io/file gh/metadata-directory))))))

(defstate ^:private organization-metadata-files :start (list-metadata-files organization-filename))
(defstate ^:private person-metadata-files       :start (list-metadata-files person-filename))
(defstate ^:private program-metadata-files      :start (list-metadata-files program-filename))
(defstate ^:private project-metadata-files      :start (list-metadata-files project-filename))
(defstate ^:private repository-metadata-files   :start (list-metadata-files repository-filename))

(defstate ^:private metadata-files
  :start { :organization organization-metadata-files
           :person       person-metadata-files
           :program      program-metadata-files
           :project      project-metadata-files
           :repository   repository-metadata-files })

(defn- list-subdirs
  "Returns a sequence of the immediate subdirectories of dir, as java.io.File objects."
  [^java.io.File dir]
  (seq (.listFiles dir
                   (reify
                     java.io.FileFilter
                     (accept [this f]
                       (.isDirectory f))))))

(defstate organizations :start (doall (map #(.getName ^java.io.File %) (list-subdirs (io/file organization-metadata-directory)))))
(defstate people        :start (doall (map #(.getName ^java.io.File %) (list-subdirs (io/file people-metadata-directory)))))
(defstate programs      :start (doall (map #(.getName ^java.io.File %) (list-subdirs (io/file program-metadata-directory)))))

(defn- clojurise-json-key
  "Converts nasty JSON String keys (e.g. \"fullName\") to nice Clojure keys (e.g. :full-name)."
  [k]
  (keyword
    (s/replace
      (s/join "-"
              (map s/lower-case
                   (s/split k #"(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")))
      "git-hub"
      "github")))

(defn- read-metadata-file-fn
  [metadata-file]
  (let [the-file (io/file metadata-file)]
    (when (.exists the-file)
      (ch/parse-string (slurp the-file) clojurise-json-key))))
(def ^:private read-metadata-file (memoize read-metadata-file-fn))

(defn- validate-metadata-file
  "Validates the given metadata file against the given schema-type, automatically determining which version the metadata file is."
  [schema-type ^java.io.File file]
  (log/debug "Validating" schema-type "metadata file" (.getCanonicalPath file))
  (try
    (let [json-string      (slurp file)
          json             (ch/parse-string json-string clojurise-json-key)
          metadata-version (:metadata-version json)
          schema-id        [schema-type metadata-version]]
      (if metadata-version
        (sch/validate-json schema-id json-string)
        (throw (Exception. (str "Unable to read metadataVersion property from " (.getCanonicalPath file))))))))

(defn- validate-metadata-files
  [schema-type files]
  (doall (map (partial validate-metadata-file schema-type) files)))

(defn validate-metadata
  "Validates all metadata in the repository."
  []
  (doall (map #(validate-metadata-files (key %) (val %)) metadata-files)))

(defn organization-metadata
  "Organization metadata of the given organization-id, or nil if there is none."
  [organization-id]
  (assoc (read-metadata-file (str organization-metadata-directory "/" organization-id "/" organization-filename))
         :organization-id organization-id))

(defn organizations-metadata
  "A seq containing the metadata of all organizations."
  []
  (map organization-metadata organizations))

(defn person-metadata
  "Person metadata of the given person-id, or nil if there is none."
  [person-id]
  (assoc (read-metadata-file (str people-metadata-directory "/" person-id "/" person-filename))
         :person-id person-id))

(defn people-metadata
  "A seq containing the metadata of all people."
  []
  (map person-metadata people))

(defn- person-metadata-by-github-id-fn
  [github-id]
  (first (filter #(some #{github-id} (:github-user-ids %)) (people-metadata))))
(def person-metadata-by-github-id
  "Person metadata of the given GitHub user id, or nil if there is none."
  (memoize person-metadata-by-github-id-fn))

(defn- program-project-repos
  "A seq of the ids of all repositories in the given program & project."
  [program-id project-id]
  (map #(.getName ^java.io.File %) (list-subdirs (io/file (str program-metadata-directory "/" program-id "/" project-id)))))

(defn- program-project-repos-metadata
  "A seq of the metadata of all repositories in the given program & project."
  [program-id project-id]
  (map #(assoc (read-metadata-file-fn (str program-metadata-directory "/" program-id "/" program-id "/" % "/" repository-filename))
               :program-id    program-id
               :project-id    project-id
               :repository-id %)
       (program-project-repos program-id)))

(defn- program-projects
  "A seq of the ids of all projects in the given program."
  [program-id]
  (map #(.getName ^java.io.File %) (list-subdirs (io/file (str program-metadata-directory "/" program-id)))))

(defn- program-projects-metadata
  "A seq containing the metadata of all projects in the given program."
  [program-id]
  (map #(assoc (read-metadata-file-fn (str program-metadata-directory "/" program-id "/" % "/" project-filename))
               :program-id   program-id
               :project-id   %
               :repositories (remove nil? (program-project-repos-metadata program-id %)))
       (program-projects program-id)))

(defn program-metadata
  "Program metadata of the given program-id, or nil if there is none."
  [program-id]
  (assoc (read-metadata-file (str program-metadata-directory "/" program-id "/" program-filename))
         :program-id program-id
         :projects   (remove nil? (program-projects-metadata program-id))))

(defn programs-metadata
  "A seq containing the metadata of all programs."
  []
  (map program-metadata programs))

(defn projects-metadata
  "A seq containing the metadata of all projects, regardless of program."
  []
  (remove nil? (map :projects (programs-metadata))))

(defn repos-metadata
  "A seq containing the metadata of all repositories, regardless of program or project."
  []
  (remove nil? (map :repositories (projects-metadata))))