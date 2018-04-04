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
(ns metadata-tool.tools.checkers
  (:require [clojure.string                 :as s]
            [clojure.set                    :as set]
            [clojure.pprint                 :as pp]
            [clojure.tools.logging          :as log]
            [clojure.java.io                :as io]
            [mount.core                     :as mnt :refer [defstate]]
            [clj-time.core                  :as tm]
            [clj-time.format                :as tf]
            [metadata-tool.utils            :as u]
            [metadata-tool.config           :as cfg]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.schemas  :as sch]
            [metadata-tool.sources.metadata :as md]))


; Local (filesystem only, no API calls) checks

(defn- check-syntax
  []
  (md/validate-metadata))

(defn- check-current-affiliations
  []
  (doall
    (map #(if (empty? (md/current-affiliations %))
            (println "⚠️ Person" % "has no current affiliations."))
         md/people)))

(defn- check-duplicate-email-addresses
  []
  (let [email-frequencies (frequencies (mapcat :email-addresses (md/people-metadata)))
        duplicate-emails  (filter #(> (get email-frequencies %) 1) (keys email-frequencies))]
    (doall (map #(println "❌ Email" % "appears more than once.") duplicate-emails))))

(defn- check-duplicate-github-ids
  []
  (let [github-id-frequencies (frequencies (mapcat :github-user-ids (md/people-metadata)))
        duplicate-github-ids  (filter #(> (get github-id-frequencies %) 1) (keys github-id-frequencies))]
    (doall (map #(println "❌ GitHub user id" % "appears more than once.") duplicate-github-ids))))

(defn- check-affiliation-references
  []
  (let [affiliation-org-ids          (mapcat #(:organization-id (:affiliations %)) (md/people-metadata))
        invalid-affiliations-org-ids (filter #(nil? (md/organization-metadata %)) affiliation-org-ids)]
    (doall (map #(println "❌ Organization id" % "(used in an affiliation) doesn't have metadata.") invalid-affiliations-org-ids))))

(defn- check-approved-contributor-references
  []
  (let [approved-contributor-person-ids         (mapcat #(:person-id (:approved-contributors %)) (md/organizations-metadata))
        invalid-approved-contributor-person-ids (filter #(nil? (md/person-metadata %)) approved-contributor-person-ids)]
    (doall (map #(println "❌ Person id" % "(used in an approved contributor) doesn't have metadata.") invalid-approved-contributor-person-ids))))

(defn- check-pmc-lead-references
  []
  (let [pmc-lead-person-ids         (map :pmc-lead (md/programs-metadata))
        invalid-pmc-lead-person-ids (filter #(nil? (md/person-metadata %)) pmc-lead-person-ids)]
    (doall (map #(println "❌ Person id" % "(a PMC lead) doesn't have metadata.") invalid-pmc-lead-person-ids))))

(defn- check-missing-working-group-chairs
  []
  (let [working-groups-with-missing-chairs (filter #(empty? (:working-group-chairs %)) (md/working-groups-metadata))]
    (doall (map #(println "⚠️ Working Group" (str (:program-id %) "/" (:activity-id %)) "doesn't have any chairs.") working-groups-with-missing-chairs))))

(defn- check-working-group-chair-references
  []
  (let [working-group-chair-person-ids         (seq (distinct (mapcat :working-group-chairs (md/working-groups-metadata))))
        invalid-working-group-chair-person-ids (filter #(nil? (md/person-metadata %)) working-group-chair-person-ids)]
    (doall (map #(println "❌ Person id" % "(a Working Group chair) doesn't have metadata.") invalid-working-group-chair-person-ids))))

(defn- check-project-states
  []
  (let [projects-with-invalid-states (remove #(boolean (some #{(:state %)} ["INCUBATING" "RELEASED" "ARCHIVED"])) (md/projects-metadata))]
    (doall (map #(println "❌ Project" (:activity-name %) "has an invalid state:" (:state %)) projects-with-invalid-states))))

(defn- check-working-group-states
  []
  (let [working-groups-with-invalid-states (remove #(boolean (some #{(:state %)} ["OPERATING" "ARCHIVED"])) (md/working-groups-metadata))]
    (doall (map #(println "❌ Working Group" (str (:program-id %) "/" (:activity-id %)) "has an invalid state:" (:state %)) working-groups-with-invalid-states))))

(defn- check-duplicate-github-urls
  []
  (let [duplicate-github-urls (filter #(> (val %) 1) (frequencies (mapcat :github-urls (md/activities-metadata))))]
    (doall (map #(println "❌ GitHub URL" (key %) "appears" (val %) "times") duplicate-github-urls))))

(defn- check-duplicate-activity-names
  "Ensure global uniqueness of activity names, since that will cause problems for systems that don't natively support programs (e.g. Bitergia)."
  []
  (let [duplicate-activity-names (filter #(> (val %) 1) (frequencies (map :activity-name (md/activities-metadata))))]
    (doall (map #(println "❌ Activity Name" (key %) "appears" (val %) "times") duplicate-activity-names))))

(defn- check-states-and-dates
  []
  (let [released-projects-without-release-dates    (filter #(and (= (:state %) "RELEASED") (nil? (:release-date %))) (md/projects-metadata))
        archived-activities-without-archived-dates (filter #(and (= (:state %) "ARCHIVED") (nil? (:archive-date %))) (md/activities-metadata))]
    (doall (map #(println "❌ Project" (str (:program-id %) "/" (:activity-id %)) "is released, but has no release date") released-projects-without-release-dates))
    (doall (map #(println "❌ Activity" (str (:program-id %) "/" (:activity-id %)) "is archived, but has no archive date") archived-activities-without-archived-dates))))

(defn check-local
  "Performs comprehensive checking of files locally on disk (no API calls out to GitHub, JIRA, etc.)."
  []
  (check-syntax)
  (check-current-affiliations)
  (check-duplicate-email-addresses)
  (check-duplicate-github-ids)
  (check-affiliation-references)
  (check-approved-contributor-references)
  (check-pmc-lead-references)
  (check-missing-working-group-chairs)
  (check-working-group-chair-references)
  (check-project-states)
  (check-working-group-states)
  (check-duplicate-github-urls)
  (check-duplicate-activity-names)
  (check-states-and-dates))





; Local and remote (filesystem and/or API call) checks


(defn- check-project-leads
  []
  (let [activities-with-github-urls (remove #(empty? (:github-urls %)) (md/activities-metadata))]
    (doall
      (map #(doall
              (map (fn [github-url]
                     (if (empty? (gh/admin-logins github-url))
                       (if (= "ARCHIVED" (:state %))
                         (println "⚠️ GitHub Repository" github-url "in archived" (s/lower-case (s/replace (:type %) "_" " ")) (str (:program-id %) "/" (:activity-id %)) "has no admins, or they haven't accepted their invitations yet.")
                         (println "❌ GitHub Repository" github-url "in" (s/lower-case (s/replace (:type %) "_" " ")) (str (:program-id %) "/" (:activity-id %)) "has no admins, or they haven't accepted their invitations yet."))))
                   (:github-urls %)))
           activities-with-github-urls))))

(defn- check-metadata-for-collaborators
  []
  (let [github-urls   (remove empty? (mapcat :github-urls (md/activities-metadata)))
        github-logins (sort (distinct (mapcat gh/collaborator-logins github-urls)))]
    (doall (map #(if-not (md/person-metadata-by-github-login %)
                   (println "❌ GitHub login" % "doesn't have any metadata."))
                github-logins))))

(defn check-remote
  "Performs checks that require API calls out to GitHub, JIRA, Bitergia, etc. (which may be rate limited)."
  []
  (check-project-leads)
  (check-metadata-for-collaborators)
;  (check-metadata-for-repos)
;  (check-bitergia-projects)
)





; Convenience fn for performing all checks

(defn check
  "Performs comprehensive checks, including API calls out to GitHub, JIRA, and Bitergia (which may be rate limited)."
  []
  (check-local)
  (check-remote))













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

