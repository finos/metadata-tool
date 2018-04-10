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
  (let [affiliation-org-ids          (seq (distinct (mapcat #(map :organization-id (:affiliations %)) (md/people-metadata))))
        invalid-affiliations-org-ids (filter #(nil? (md/organization-metadata %)) affiliation-org-ids)]
    (doall (map #(println "❌ Organization id" % "(used in an affiliation) doesn't have metadata.") invalid-affiliations-org-ids))))

(defn- check-approved-contributor-references
  []
  (let [approved-contributor-person-ids         (seq (distinct (mapcat #(:person-id (:approved-contributors %)) (md/organizations-metadata))))
        invalid-approved-contributor-person-ids (filter #(nil? (md/person-metadata %)) approved-contributor-person-ids)]
    (doall (map #(println "❌ Person id" % "(used in an approved contributor) doesn't have metadata.") invalid-approved-contributor-person-ids))))

(defn- check-pmc-lead-references
  []
  (let [pmc-lead-person-ids         (seq (distinct (map :pmc-lead (md/programs-metadata))))
        invalid-pmc-lead-person-ids (filter #(nil? (md/person-metadata %)) pmc-lead-person-ids)]
    (doall (map #(println "❌ Person id" % "(a PMC lead) doesn't have metadata.") invalid-pmc-lead-person-ids))))

(defn- check-missing-working-group-chairs
  []
  (let [working-groups-with-missing-chairs (filter #(empty? (:working-group-chairs %)) (md/working-groups-metadata))]
    (doall (map #(println "⚠️ Working Group" (str (:program-id %) "/" (:activity-id %)) "doesn't have any chairs.") working-groups-with-missing-chairs))))

(defn- check-working-group-chair-references
  []
  (let [working-group-chair-person-ids         (seq (distinct (mapcat :working-group-chairs (md/activities-metadata))))
        invalid-working-group-chair-person-ids (filter #(nil? (md/person-metadata %)) working-group-chair-person-ids)]
    (doall (map #(println "❌ Person id" % "(a Working Group chair) doesn't have metadata.") invalid-working-group-chair-person-ids))))

(defn- check-project-chairs
  []
  (let [projects-with-chairs (sort-by :activity-id (filter :working-group-chairs (md/projects-metadata)))]
    (doall (map #(println "⚠️ Project" (str (:program-id %) "/" (:activity-id %)) "has working group chairs.") projects-with-chairs))))

(defn- check-project-states
  []
  (let [projects-with-invalid-states (remove #(boolean (some #{(:state %)} ["INCUBATING" "RELEASED" "ARCHIVED"])) (md/projects-metadata))]
    (doall (map #(println "❌ Project" (:activity-name %) "has an invalid state:" (:state %)) projects-with-invalid-states))))

(defn- check-working-group-states
  []
  (let [working-groups-with-invalid-states (remove #(boolean (some #{(:state %)} ["OPERATING" "PAUSED" "ARCHIVED"])) (md/working-groups-metadata))]
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
  (check-duplicate-github-ids)
  (check-affiliation-references)
  (check-approved-contributor-references)
  (check-pmc-lead-references)
  (check-missing-working-group-chairs)
  (check-working-group-chair-references)
  (check-project-chairs)
  (check-project-states)
  (check-working-group-states)
  (check-duplicate-github-urls)
  (check-duplicate-activity-names)
  (check-states-and-dates)
  (check-github-coords)
  (check-mailing-list-addresses))





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
  (let [project-names                         (set (map :activity-name (md/projects-metadata)))
        projects-missing-from-bitergia-git    (sort (set/difference project-names (set (bi/all-projects-git))))
        projects-missing-from-bitergia-github (sort (set/difference project-names (set (bi/all-projects-github))))]
    (doall (map #(println "⚠️ Project" % "is missing from Bitergia's git index (expected if its repositories are empty).")
                projects-missing-from-bitergia-git))
    (doall (map #(println "⚠️ Project" % "is missing from Bitergia's github index (expected if its repositories have never had any issues or PRs).")
                projects-missing-from-bitergia-github))))

(defn check-remote
  "Performs checks that require API calls out to GitHub, JIRA, Bitergia, etc. (which may be rate limited)."
  []
  (check-project-leads)
  (check-metadata-for-collaborators)
  (check-metadata-for-repos)
  (check-bitergia-projects))





; Convenience fn for performing all checks

(defn check
  "Performs comprehensive checks, including API calls out to GitHub, JIRA, and Bitergia (which may be rate limited)."
  []
  (check-local)
  (check-remote))
