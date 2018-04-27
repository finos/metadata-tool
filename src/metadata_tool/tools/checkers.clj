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
            [metadata-tool.utils            :as u]
            [metadata-tool.config           :as cfg]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.schemas  :as sch]
            [metadata-tool.sources.metadata :as md]))


; Utility fns
(defn- type-to-string
  [type]
  (if-not (s/blank? type)
    (s/lower-case (s/replace type "_" " "))))

(defn- state-to-string
  [state]
  (if-not (s/blank? state)
    (str (s/upper-case (first state))
         (s/join (rest (s/lower-case state))))))

(defn- activity-id-to-string
  [activity]
  (if activity
    (str (:program-id activity) "/" (:activity-id activity))))

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

(defn- check-duplicate-github-logins
  []
  (let [github-login-frequencies (frequencies (mapcat :github-logins (md/people-metadata)))
        duplicate-github-logins  (filter #(> (get github-login-frequencies %) 1) (keys github-login-frequencies))]
    (doall (map #(println "❌ GitHub login" % "appears more than once.") duplicate-github-logins))))

(defn- check-affiliation-references
  []
  (let [affiliation-org-ids          (seq (distinct (mapcat #(map :organization-id (:affiliations %)) (md/people-metadata))))
        invalid-affiliations-org-ids (filter #(nil? (md/organization-metadata %)) affiliation-org-ids)]
    (doall (map #(println "❌ Organization id" % "(used in an affiliation) doesn't have metadata.") invalid-affiliations-org-ids))))

(defn- check-approved-contributor-references
  []
  (let [approved-contributors                   (remove nil? (mapcat :approved-contributors (md/organizations-metadata)))
        approved-contributor-person-ids         (seq (distinct (map :person-id approved-contributors)))
        invalid-approved-contributor-person-ids (filter #(nil? (md/person-metadata %)) approved-contributor-person-ids)]
    (doall (map #(println "❌ Person id" % "(used in an approved contributor) doesn't have metadata.") invalid-approved-contributor-person-ids))))

(defn- check-pmc-lead-references
  []
  (let [pmc-lead-person-ids         (seq (distinct (map :pmc-lead (md/programs-metadata))))
        invalid-pmc-lead-person-ids (filter #(nil? (md/person-metadata %)) pmc-lead-person-ids)]
    (doall (map #(println "❌ Person id" % "(a PMC lead) doesn't have metadata.") invalid-pmc-lead-person-ids))))

(defn- check-missing-lead-or-chair
  []
  (let [activities-with-missing-lead-or-chair (sort-by activity-id-to-string (filter #(nil? (:lead-or-chair %)) (md/activities-metadata)))]
    (doall (map #(if (= "ARCHIVED" (:state %))
                   (println "⚠️ Archived" (type-to-string (:type %)) (activity-id-to-string %) "doesn't have a" (str (if (= "PROJECT" (:type %)) "lead" "chair") "."))
                   (println "❌" (state-to-string (:state %)) (type-to-string (:type %)) (activity-id-to-string %) "doesn't have a" (str (if (= "PROJECT" (:type %)) "lead" "chair") ".")))
                 activities-with-missing-lead-or-chair))))

(defn- check-lead-or-chair-references
  []
  (let [lead-or-chair-person-ids                    (seq (distinct (remove nil? (map #(:person-id (:lead-or-chair %)) (md/activities-metadata)))))
        invalid-lead-or-chair-person-ids-person-ids (filter #(nil? (md/person-metadata %)) lead-or-chair-person-ids)]
    (doall (map #(println "❌ Person id" % "(a Project Lead or Working Group chair) doesn't have metadata.") invalid-lead-or-chair-person-ids-person-ids))))

(defn- check-project-states
  []
  (let [projects-with-invalid-states (remove #(boolean (some #{(:state %)} ["INCUBATING" "RELEASED" "ARCHIVED"])) (md/projects-metadata))]
    (doall (map #(println "❌ Project" (activity-id-to-string %) "has an invalid state:" (:state %)) projects-with-invalid-states))))

(defn- check-working-group-states
  []
  (let [working-groups-with-invalid-states (remove #(boolean (some #{(:state %)} ["OPERATING" "PAUSED" "ARCHIVED"])) (md/working-groups-metadata))]
    (doall (map #(println "❌ Working Group" (activity-id-to-string %) "has an invalid state:" (:state %)) working-groups-with-invalid-states))))

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
    (doall (map #(println "❌ Project" (activity-id-to-string %) "is released, but has no release date") released-projects-without-release-dates))
    (doall (map #(println "❌ Activity" (activity-id-to-string %) "is archived, but has no archive date") archived-activities-without-archived-dates))))

(defn- check-github-coords
  []
  (let [programs-without-github-org                     (filter #(nil? (:github-org %)) (md/programs-metadata))
        programs-without-github-org-with-activity-repos (filter #(seq (mapcat :github-repos (:activities %))) programs-without-github-org)]
    (doall (map #(println "⚠️ Program" (str (:program-id %)) "does not have a GitHub org.") programs-without-github-org))
    (doall (map #(println "❌ Program" (str (:program-id %)) "does not have a GitHub org, but some of its activities have GitHub repos.") programs-without-github-org-with-activity-repos))))

(defn- check-mailing-list-addresses
  []
  (let [programs-metadata                (md/programs-metadata)
        activities-metadata              (mapcat :activities programs-metadata)
        unknown-program-email-addresses  (remove #(or (s/ends-with? % "@finos.org")
                                                      (s/ends-with? % "@symphony.foundation"))
                                                 (remove s/blank? (mapcat #(vec [(:pmc-mailing-list-address         %)
                                                                                 (:pmc-private-mailing-list-address %)
                                                                                 (:program-mailing-list-address     %)])
                                                                          programs-metadata)))
        unknown-activity-email-addresses (remove #(or (s/ends-with? % "@finos.org")
                                                      (s/ends-with? % "@symphony.foundation"))
                                                 (remove s/blank? (mapcat :mailing-list-addresses activities-metadata)))]
    (doall (map #(println "⚠️ Mailing list address" % "(a program-level mailing list) does not appear to be Foundation-managed.") unknown-program-email-addresses))
    (doall (map #(println "⚠️ Mailing list address" % "(an activity-level mailing list) does not appear to be Foundation-managed.") unknown-activity-email-addresses))))


(defn check-local
  "Performs comprehensive checking of files locally on disk (no API calls out to GitHub, JIRA, etc.)."
  []
  (check-syntax)
  (check-current-affiliations)
  (check-duplicate-email-addresses)
  (check-duplicate-github-logins)
  (check-affiliation-references)
  (check-approved-contributor-references)
  (check-pmc-lead-references)
  (check-missing-lead-or-chair)
  (check-lead-or-chair-references)
  (check-project-states)
  (check-working-group-states)
  (check-duplicate-github-urls)
  (check-duplicate-activity-names)
  (check-states-and-dates)
  (check-github-coords)
  (check-mailing-list-addresses))





; Local and remote (filesystem and/or API call) checks


(defn- check-repo-admins
  []
  (let [activities-with-github-urls (remove #(empty? (:github-urls %)) (md/activities-metadata))]
    (doall
      (map #(doall
              (map (fn [github-url]
                     (if (empty? (gh/admin-logins github-url))
                       (if (= "ARCHIVED" (:state %))
                         (println "⚠️ GitHub Repository" github-url "in archived" (type-to-string (:type %)) (activity-id-to-string %) "has no admins, or they haven't accepted their invitations yet.")
                         (println "❌ GitHub Repository" github-url "in" (type-to-string (:type %)) (activity-id-to-string %) "has no admins, or they haven't accepted their invitations yet."))))
                   (:github-urls %)))
           activities-with-github-urls))))

(defn- check-metadata-for-collaborators
  []
  (let [github-urls   (mapcat :github-urls (md/activities-metadata))
        github-logins (sort (distinct (mapcat gh/collaborator-logins github-urls)))]
    (doall (map #(if-not (md/person-metadata-by-github-login %)
                   (println "❌ GitHub login" % "doesn't have any metadata."))
                github-logins))))

(defn- check-metadata-for-repos
  []
  (let [github-repo-urls       (set (map s/lower-case (remove s/blank? (mapcat #(gh/repos-urls (:github-url %)) (md/programs-metadata)))))
        metadata-repo-urls     (set (map s/lower-case (remove s/blank? (mapcat :github-urls (md/activities-metadata)))))
        repos-without-metadata (sort (set/difference github-repo-urls metadata-repo-urls))
        metadata-without-repo  (sort (set/difference metadata-repo-urls github-repo-urls))]
    (doall (map #(println "❌ GitHub repo" % "has no metadata.") repos-without-metadata))
    (doall (map #(println "❌ GitHub repo" % "has metadata, but does not exist in GitHub.") metadata-without-repo))))

(defn- check-bitergia-projects
  []
  (let [project-names                  (set (map :activity-name (md/projects-metadata)))
        projects-missing-from-bitergia (sort (set/difference project-names (bi/all-projects)))]
    (doall (map #(println "⚠️ Project" % "is missing from the Bitergia indexes.")
                projects-missing-from-bitergia))))

(defn check-remote
  "Performs checks that require API calls out to GitHub, JIRA, Bitergia, etc. (which may be rate limited)."
  []
  (check-repo-admins)
  (check-metadata-for-collaborators)
  (check-metadata-for-repos)
  (check-bitergia-projects))





; Convenience fn for performing all checks

(defn check
  "Performs comprehensive checks, including API calls out to GitHub, JIRA, and Bitergia (which may be rate limited)."
  []
  (check-local)
  (check-remote))
