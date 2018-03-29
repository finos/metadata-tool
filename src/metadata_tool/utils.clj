;
; Copyright © 2017 FINOS Foundation
; SPDX-License-Identifier: Apache-2.0
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
(ns metadata-tool.utils
  (:require [clojure.string                 :as s]
            [clojure.set                    :as set]
            [clojure.pprint                 :as pp]
            [clojure.tools.logging          :as log]
            [clojure.java.io                :as io]
            [cheshire.core                  :as ch]
            [clj-time.core                  :as tm]
            [clj-time.format                :as tf]
            [metadata-tool.config           :as cfg]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.schemas  :as sch]
            [metadata-tool.sources.metadata :as md]))

(defn println-stderr
  "Exactly like clojure.core/println, but output goes to stderr."
  [& args]
  (binding [*out* *err*]
    (println (s/join " " args)))
  nil)

(comment
(defn is-project
  [project]
  (let [project-name (get project "projectName")]
    (not-any? #(= project-name %) cfg/not-a-project-list)))

(defn get-project-langs
  "Returns a list of language maps, one for each repo that is part of a given project"
  [project-pair]
  (let [repos (nth project-pair 1)
        langs (map #(gh/repo-langs %) repos)]
    langs))

(defn get-project-stats
  "Returns a list of repo stats, one for each repo that is part of a given project"
  [project-pair]
  (let [repos (nth project-pair 1)
        stats (map #(gh/repo-stats (get % "repositoryName")) repos)]
    stats))

(def non-projects
  "Projects that are placeholders for repositories that aren't really projects."
  #{"Foundation Infrastructure" "Documentation and Examples"})

(def datetime-formatter
   "e.g. 2017-08-17T07:31:55.543Z"
   (tf/formatters :date-time))

(def date-formatter
   "e.g. 2017-08-17"
   (tf/formatters :date))

(def human-readable-formatter
   "e.g. 2017-08-17 7:31AM UTC"
   (tf/formatter "yyyy-MM-dd h:mmaa ZZZ"))

(defn now-as-string
  "Returns the current date/time as a string, using the given formatter (defaults to human-readable-formatter if not provided)."
  ([] (now-as-string human-readable-formatter))
  ([formatter]
   (tf/unparse formatter (tm/now))))

(defn user-as-string
  "Returns an email-compatible string representation of a user JSON structure, in the format \"full name (first-github-id)\" <first@email.address>."
  ([user-json] (user-as-string user-json true))
  ([user-json include-github-id?]
   (if user-json
     (let [full-name     (get user-json "fullName")
           github-id     (first (get user-json "gitHubUserIds"))
           email-address (first (get user-json "emailAddresses"))
           result        (s/trim (str "\""
                                      full-name
                                      (if (and include-github-id? github-id) (str " (" github-id ")"))
                                      "\""
                                      (if email-address (str " <" email-address ">"))))]
       (when-not (s/blank? result)
         result)))))

(defn get-project-name
  "Return the name of the project, given a repository JSON structure."
  [repo-json]
  (if-let [project-name (get repo-json "projectName")]
    project-name
    (get repo-json "repositoryName")))

(defn all-projects-including-placeholders
  "Returns a map where the keys are the names of the projects (including placeholders - Foundation Infrastructure etc.), and the values are a sequence of repository JSONs comprising that project."
  []
  (let [repo-files md/program-metadata-files   ;####TODO: ADDRESS FALLOUT HERE!!!!
        repo-jsons (map #(ch/parse-string (slurp %)) repo-files)]
    (group-by get-project-name repo-jsons)))

(defn all-projects
  "Returns a map where the keys are the names of the projects (excluding placeholders - Foundation Infrastructure etc.), and the values are a sequence of repository JSONs comprising that project."
  []
  (apply dissoc (all-projects-including-placeholders) non-projects))

(defn project-states
  "Returns all of the lifecycle states for the given project (which, if the data is valid, should always be a single entry)."
  [prj]
  (distinct (remove nil? (map #(get % "projectState") (val prj)))))

(defn project-state
  "Returns the lifecycle state of the given project, or nil if it doesn't have one (i.e. the project is a placeholder)."
  [prj]
  (first (project-states prj)))

(defn- projects-by-state
  "Returns a map where the keys are names of the projects in the given lifecycle state, and the values are a sequence of repository JSONs comprising that project."
  [state]
  (apply hash-map (flatten (filter #(= state (project-state %)) (all-projects)))))

(defn incubating-projects
  "Returns a map where the keys are names of incubating projects, and the values are a sequence of repository JSONs comprising that project."
  []
  (projects-by-state "INCUBATING"))

(defn released-projects
  "Returns a map where the keys are names of released projects, and the values are a sequence of repository JSONs comprising that project."
  []
  (projects-by-state "RELEASED"))

(defn archived-projects
  "Returns a map where the keys are names of archived projects, and the values are a sequence of repository JSONs comprising that project."
  []
  (projects-by-state "ARCHIVED"))

(defn inactive-projects
  "Returns a map where the keys are the names of inactive projects, the values are a sequence of repository JSONs comprising that project."
  []
  (let [projects               (all-projects)
        inactive-project-names (set/difference (bi/inactive-projects) non-projects)]
    (select-keys projects inactive-project-names)))

(defn inactive-non-archived-projects
  "Returns a map where the keys are the names of inactive projects that are not archived, the values are a sequence of repository JSONs comprising that project."
  []
  (let [projects                             (all-projects)
        inactive-project-names               (set (keys (inactive-projects)))
        archived-project-names               (set (keys (archived-projects)))
        inactive-non-archived-projects-names (set/difference inactive-project-names archived-project-names)]
    (select-keys projects inactive-non-archived-projects-names)))

(defn active-projects-with-old-prs
  "Returns a map of active projects with old PRs.  Keys are project names, values are a sequence of repository JSONs comprising that project."
  []
  (let [projects                          (all-projects)
        active-projects                   (bi/active-projects)
        projects-with-old-prs             (bi/projects-with-old-prs)
        active-project-names-with-old-prs (remove #(= "Foundation Infrastructure" %) (set/intersection active-projects projects-with-old-prs))]
    (select-keys projects active-project-names-with-old-prs)))

(defn active-projects-with-old-issues
  "Returns a map of active projects with old issues.  Keys are project names, values are a sequence of repository JSONs comprising that project."
  []
  (let [projects                             (all-projects)
        active-projects                      (bi/active-projects)
        projects-with-old-issues             (bi/projects-with-old-issues)
        active-project-names-with-old-issues (remove #(= "Foundation Infrastructure" %) (set/intersection active-projects projects-with-old-issues))]
    (select-keys projects active-project-names-with-old-issues)))
)

(defn contains-val?
  "Like contains?, but works for values not indexes."
  [coll value]
  (boolean (some #{ value } coll)))
