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
(ns metadata-tool.tools.checkers
  (:require [clojure.string                 :as s]
            [clojure.set                    :as set]
            [clojure.pprint                 :as pp]
            [clojure.tools.logging          :as log]
            [clojure.java.io                :as io]
            [mount.core                     :as mnt :refer [defstate]]
            [cheshire.core                  :as ch]
            [clj-time.core                  :as tm]
            [clj-time.format                :as tf]
            [metadata-tool.utils            :as u]
            [metadata-tool.config           :as cfg]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.schemas  :as sch]
            [metadata-tool.sources.metadata :as md]))

(defn- check-syntax
  []
  (md/validate-metadata))

(comment
(defn- check-parent-folder-name
  [^java.io.File file expected-name type]
  (let [folder-name (.getName (io/file (.getParent file)))]
    (if (not= expected-name folder-name)
      (println "❌" type expected-name "is in folder" folder-name))))


(defn- check-organization-1-0-0-references
  [^java.io.File file json]
  (let [org-id                (get json "organizationId")
        approved-contributors (map #(get % "personId") (get json "approvedContributors"))]
    ; Validate organization id and folder name match
    (check-parent-folder-name file org-id "Organization")
    ))

(defn- check-organization-references
  [^java.io.File organization-file]
  (let [json    (ch/parse-string (slurp organization-file))
        version (get json "metadataVersion")]
    (case version
      "1.0.0" (check-organization-1-0-0-references organization-file json)
      ; else
              (throw (RuntimeException. (str "Unknown organization version: " version))))))

(defn- check-program-2-0-0-references
  [^java.io.File file json]
  (let [repo-name (get json "repositoryName")]
    ; Validate repo name and folder name match
    (check-parent-folder-name file repo-name "Repository")))

(defn- check-program-references
  [^java.io.File program-file]
  (let [json    (ch/parse-string (slurp program-file))
        version (get json "metadataVersion")]
    (case version
      "2.0.0" (check-program-2-0-0-references program-file json)
      ; else
              (throw (RuntimeException. (str "Unknown project version: " version))))))

(defn- check-person-1-0-0-references
  [^java.io.File file json]
  (let [user-id (get json "gitHubUserId")]
    ; Validate user id and folder name match
    (check-parent-folder-name file user-id "User")))

(defn- check-person-1-1-0-references
  [file json]
  (check-person-1-0-0-references file json))  ; No changes in references between 1.0.0 and 1.1.0

(defn- check-person-2-0-0-references
  [^java.io.File file json]
  (check-person-1-0-0-references file json)
  ; Validate all organizationIds in affiliations list
  (let [organization-ids (map #(get % "organizationId") (get json "affiliations"))]
    (doall (map #(let [org-details (md/organization-metadata %)]
                    (when-not org-details
                      (println "❌ Organization id" % "in file" (.getCanonicalPath file) "has no metadata")))
                organization-ids))))

(defn- check-person-3-0-0-references
  [^java.io.File file json]
  ; Validate all organizationIds in affiliations list
  (let [organization-ids (map #(get % "organizationId") (get json "affiliations"))]
    (doall (map #(let [org-details (md/organization-metadata %)]
                    (when-not org-details
                      (println "❌ Organization id" % "in file" (.getCanonicalPath file) "has no metadata")))
                organization-ids))))

(defn- check-person-references
  [^java.io.File user-file]
  (let [json    (ch/parse-string (slurp user-file))
        version (get json "metadataVersion")]
    (case version
      "1.0.0" (check-person-1-0-0-references user-file json)
      "1.1.0" (check-person-1-1-0-references user-file json)
      "2.0.0" (check-person-2-0-0-references user-file json)
      "3.0.0" (check-person-3-0-0-references user-file json)
      "3.0.1" (check-person-3-0-0-references user-file json)
      ; else
              (throw (RuntimeException. (str "Unknown user version: " version))))))

(defn- check-organizations-references
  []
  (doall (map check-organization-references md/organization-metadata-files)))

(defn- check-programs-references
  []
  (doall (map check-program-references md/program-metadata-files)))

(defn- check-people-references
  []
  (doall (map check-person-references md/people-metadata-files)))


(defn- check-github-ids
  []
  ;####TODO!!!!
  )

(defn- check-contribs
  []
  ;####TODO!!!!
  )

(defn- check-references
  []
  (check-organizations-references)
  (check-people-references)
  (check-program-references))

(defn- check-project-leads
  []
  (doall
    (map #(if (empty? (gh/project-lead-names %)) (println "⚠️ Repository" % "has no admins, or they haven't accepted their invitations yet."))
         (gh/repo-names))))

(defn- check-affiliations
  []
  (let [user-jsons (map #(ch/parse-string (slurp %)) md/people-metadata-files)]
    (doall
      (map #(if (empty? (get % "affiliations"))
              (println "⚠️ User" (get % "fullName") "has no affiliations"))
           user-jsons))))

(defn- check-email-addresses
  []
  (let [user-jsons        (map #(ch/parse-string (slurp %)) md/people-metadata-files)
        email-frequencies (frequencies (mapcat #(get % "emailAddresses") user-jsons))
        duplicate-emails  (filter #(> (get email-frequencies %) 1) (keys email-frequencies))]
    (doall
      (map #(println "❌ Email" % "appears more than once.") duplicate-emails))))

(defn- check-github-ids
  []
  (let [user-jsons            (map #(ch/parse-string (slurp %)) md/people-metadata-files)
        github-id-frequencies (frequencies (mapcat #(get % "gitHubUserIds") user-jsons))
        duplicate-github-ids  (filter #(> (get github-id-frequencies %) 1) (keys github-id-frequencies))]
    (doall
      (map #(println "❌ GitHub user id" % "appears more than once.") duplicate-github-ids))))

(defn- check-project-lifecycle-states
  []
  (let [all-projects (u/all-projects)]
    (doall
      (map #(when-not (= 1 (count (u/project-states %)))
            (println "❌ Project" (key %) "has" (count (u/project-states %)) "project states (it should have exactly 1)."))
           all-projects))))

(defn- check-bitergia-projects
  []
  (let [projects-from-metadata        (sort (keys (u/all-projects-including-placeholders)))
        projects-from-bitergia-git    (sort (bi/all-projects-git))
        projects-from-bitergia-github (sort (bi/all-projects-github))]
    (doall (map #(println "⚠️ Project" % "is missing from Bitergia git index (this is expected when its repositories are empty).")
                (sort (remove #(some #{%} projects-from-bitergia-git) projects-from-metadata))))
    (doall (map #(println "⚠️ Project" % "is missing from Bitergia github index (this is expected if its repositories have never had any issues or PRs).")
                (sort (remove #(some #{%} projects-from-bitergia-github) projects-from-metadata))))))
)

; ---- TOOL FNs ----
; Remember to add any new tool fns to the "tools" map down below, otherwise they won't be visible

(defn check-local
  "Performs comprehensive checking of files locally on disk (no API calls out to GitHub, JIRA, etc.)."
  []
  (check-syntax)
;  (check-references)
;  (check-affiliations)
;  (check-email-addresses)
;  (check-github-ids)
;  (check-project-lifecycle-states)
)

(defn check
  "Performs comprehensive checks, including API calls out to GitHub, JIRA, and Bitergia (which may be rate limited)."
  []
  (check-local)
;  (check-project-leads)
;  (check-github-ids)
;  (check-contribs)
;  (check-bitergia-projects)
)
