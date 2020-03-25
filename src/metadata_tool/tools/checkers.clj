;
; Copyright 2017 Fintech Open Source Foundation
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
  (:require [clojure.string                 :as str]
            [clojure.set                    :as set]
            [metadata-tool.exit-code        :as ec]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.metadata :as md]))


; Utility fns



(defn- type-to-string
  [type]
  (if-not (str/blank? type)
    (str/lower-case (str/replace type "_" " "))))

(defn- state-to-string
  [state]
  (if-not (str/blank? state)
    (str (str/upper-case (first state))
         (str/join (rest (str/lower-case state))))))

(defn- activity-to-string
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
           (println "ℹ️ Person" % "has no current affiliations."))
        md/people)))

(defn- check-duplicate-email-addresses
  []
  (let [email-frequencies (frequencies (mapcat :email-addresses (md/people-metadata)))
        duplicate-emails  (filter #(> (get email-frequencies %) 1) (keys email-frequencies))]
    (if (pos? (count duplicate-emails)) (ec/set-error))
    (doall (map #(println "❌ Email" % "appears more than once.") duplicate-emails))))

(defn- check-duplicate-github-logins
  []
  (let [github-login-frequencies (frequencies (mapcat :github-logins (md/people-metadata)))
        duplicate-github-logins  (filter #(> (get github-login-frequencies %) 1) (keys github-login-frequencies))]
    (if (pos? (count duplicate-github-logins)) (ec/set-error))
    (doall (map #(println "❌ GitHub login" % "appears more than once.") duplicate-github-logins))))

(defn- check-affiliation-references
  []
  (let [affiliation-org-ids          (seq (distinct (mapcat #(map :organization-id (:affiliations %)) (md/people-metadata))))
        invalid-affiliations-org-ids (filter #(nil? (md/organization-metadata %)) affiliation-org-ids)]
    (if (pos? (count invalid-affiliations-org-ids)) (ec/set-error))
    (doall (map #(println "❌ Organization id" % "(used in an affiliation) doesn't have metadata.") invalid-affiliations-org-ids))))

(defn- check-approved-contributor-references
  []
  (let [approved-contributors                   (remove nil? (mapcat :approved-contributors (md/organizations-metadata)))
        approved-contributor-person-ids         (seq (distinct (map :person-id approved-contributors)))
        invalid-approved-contributor-person-ids (filter #(nil? (md/person-metadata %)) approved-contributor-person-ids)]
    (if (pos? (count invalid-approved-contributor-person-ids)) (ec/set-error))
    (doall (map #(println "❌ Person id" % "(used in an approved contributor) doesn't have metadata.") invalid-approved-contributor-person-ids))))

(defn- check-missing-pmc-lead
  []
  (let [programs-with-missing-pmc-lead (filter #(nil? (:pmc-lead %)) (md/programs-metadata))]
    (doall (map #(println "⚠️ Program" (:program-id %) "doesn't have a PMC lead.") programs-with-missing-pmc-lead))))

(defn- check-pmc-lead-references
  []
  (let [pmc-lead-person-ids             (seq (distinct (map :pmc-lead (md/programs-metadata))))
        invalid-pmc-lead-person-ids     (filter #(nil? (md/person-metadata %)) (remove nil? pmc-lead-person-ids))]
    (if (pos? (count invalid-pmc-lead-person-ids)) (ec/set-error))
    (doall (map #(println "❌ Person id" % "(a PMC lead) doesn't have metadata.") invalid-pmc-lead-person-ids))))

(defn- check-missing-lead-or-chair
  []
  (let [activities-with-missing-lead-or-chair (sort-by activity-to-string (filter #(str/blank? (:lead-or-chair-person-id %)) (md/activities-metadata)))]
    (doall (map #(if (= "ARCHIVED" (:state %))
                   (println "ℹ️ Archived activity" (activity-to-string %) "doesn't have a" (str (if (= "PROJECT" (:type %)) "lead" "chair") "."))
                   (println "⚠️" (state-to-string (:state %)) (type-to-string (:type %)) (activity-to-string %) "doesn't have a" (str (if (= "PROJECT" (:type %)) "lead" "chair") ".")))
                activities-with-missing-lead-or-chair))))

(defn- check-lead-or-chair-references
  []
  (let [lead-or-chair-person-ids                    (seq (distinct (keep :lead-or-chair-person-id (md/activities-metadata))))
        invalid-lead-or-chair-person-ids-person-ids (filter #(nil? (md/person-metadata %)) lead-or-chair-person-ids)]
    (if (pos? (count invalid-lead-or-chair-person-ids-person-ids)) (ec/set-error))
    (doall (map #(println "❌ Person id" % "(a Project Lead or Working Group chair) doesn't have metadata.") invalid-lead-or-chair-person-ids-person-ids))))

(defn- check-project-states
  []
  (let [projects-with-invalid-states (remove #(boolean (some #{(:state %)} ["ACTIVE" "INCUBATING" "RELEASED" "ARCHIVED"])) (md/projects-metadata))]
    (if (pos? (count projects-with-invalid-states)) (ec/set-error))
    (doall (map #(println "❌ Project" (activity-to-string %) "has an invalid state:" (:state %)) projects-with-invalid-states))))

;; Deprecated
;; (defn- check-working-group-states
;;   []
;;   (let [working-groups-with-invalid-states (remove #(boolean (some #{(:state %)} ["ACTIVE" "OPERATING" "PAUSED" "ARCHIVED"])) (md/working-groups-metadata))]
;;     (if (pos? (count working-groups-with-invalid-states)) (ec/set-error))
;;     (doall (map #(println "❌ Working Group" (activity-to-string %) "has an invalid state:" (:state %)) working-groups-with-invalid-states))))

(defn- check-duplicate-github-urls
  []
  (let [duplicate-github-urls (filter #(> (val %) 1) (frequencies (mapcat :github-urls (md/activities-metadata))))]
    (if (pos? (count duplicate-github-urls)) (ec/set-error))
    (doall (map #(println "❌ GitHub URL" (key %) "appears" (val %) "times") duplicate-github-urls))))

(defn- check-duplicate-activity-names
  "Ensure global uniqueness of activity names, since that will cause problems for systems that don't natively support programs (e.g. Bitergia)."
  []
  (let [duplicate-activity-names (filter #(> (val %) 1) (frequencies (map :activity-name (md/activities-metadata))))]
    (if (pos? (count duplicate-activity-names)) (ec/set-error))
    (doall (map #(println "❌ Activity Name" (key %) "appears" (val %) "times") duplicate-activity-names))))

(defn- check-states-and-dates
  []
  (let [released-projects-without-release-dates    (filter #(and (= (:state %) "RELEASED") (nil? (:release-date %))) (md/projects-metadata))
        archived-activities-without-archived-dates (filter #(and (= (:state %) "ARCHIVED") (nil? (:archive-date %))) (md/activities-metadata))]
    (if (pos? (count released-projects-without-release-dates)) (ec/set-error))
    (doall (map #(println "❌ Project" (activity-to-string %) "is released, but has no release date") released-projects-without-release-dates))
    (if (pos? (count archived-activities-without-archived-dates)) (ec/set-error))
    (doall (map #(println "❌ Activity" (activity-to-string %) "is archived, but has no archive date") archived-activities-without-archived-dates))))

(defn- check-github-coords
  []
  (let [programs-without-github-org                     (filter #(nil? (:github-org %)) (md/programs-metadata))
        programs-without-github-org-with-activity-repos (filter #(seq (mapcat :github-repos (:activities %))) programs-without-github-org)]
    (doall (map #(println "⚠️ Program" (str (:program-id %)) "does not have a GitHub org.") programs-without-github-org))
    (if (pos? (count programs-without-github-org-with-activity-repos)) (ec/set-error))
    (doall (map #(println "❌ Program" (str (:program-id %)) "does not have a GitHub org, but some of its activities have GitHub repos.") programs-without-github-org-with-activity-repos))))

(defn- check-mailing-list-addresses
  []
  (let [programs-metadata                (md/programs-metadata)
        activities-metadata              (mapcat :activities programs-metadata)
        unknown-program-email-addresses  (remove #(or (str/ends-with? % "@finos.org")
                                                      (str/ends-with? % "@symphony.foundation"))
                                                 (remove str/blank? (mapcat #(vec [(:pmc-mailing-list-address         %)
                                                                                 (:pmc-private-mailing-list-address %)
                                                                                 (:program-mailing-list-address     %)])
                                                                          programs-metadata)))
        unknown-activity-email-addresses (remove #(or (str/ends-with? % "@finos.org")
                                                      (str/ends-with? % "@symphony.foundation"))
                                                 (remove str/blank? (mapcat :mailing-list-addresses activities-metadata)))]
    (if (pos? (count unknown-program-email-addresses)) (ec/set-error))
    (doall (map #(println "❌ Mailing list address" % "(a program-level mailing list) does not appear to be Foundation-managed.") unknown-program-email-addresses))
    (if (pos? (count unknown-activity-email-addresses)) (ec/set-error))
    (doall (map #(println "❌ Mailing list address" % "(an activity-level mailing list) does not appear to be Foundation-managed.") unknown-activity-email-addresses))))

(defn check-local
  "Performs comprehensive checking of files locally on disk (no API calls out to GitHub, JIRA, etc.)."
  []
  (check-syntax)
  (check-current-affiliations)
  (check-duplicate-email-addresses)
  (check-duplicate-github-logins)
  (check-affiliation-references)
  (check-approved-contributor-references)
  (check-missing-pmc-lead)
  (check-pmc-lead-references)
  (check-missing-lead-or-chair)
  (check-lead-or-chair-references)
  (check-project-states)
  ;; Deprecated
  ;; (check-working-group-states)
  (check-duplicate-github-urls)
  (check-duplicate-activity-names)
  (check-states-and-dates)
  (check-github-coords)
  (check-mailing-list-addresses))





; Local and remote (filesystem and/or API call) checks


; DEPRECATED
(defn- check-repo-admins
  []
  (let [activities-with-github-urls (sort-by activity-to-string (remove #(empty? (:github-urls %)) (md/activities-metadata)))]
    (doall
     (map #(doall
            (map (fn [github-url]
                   (if (empty? (gh/admin-logins github-url))
                     (if (= "ARCHIVED" (:state %))
                       (println "ℹ️ GitHub Repository" github-url "in archived" (type-to-string (:type %)) (activity-to-string %) "has no admins, or they haven't accepted their invitations yet.")
                       (println "⚠️ GitHub Repository" github-url "in" (type-to-string (:type %)) (activity-to-string %) "has no admins, or they haven't accepted their invitations yet."))))
                 (:github-urls %)))
          activities-with-github-urls))))

(defn- check-metadata-for-collaborators
  []
  (let [github-urls   (mapcat :github-urls (md/activities-metadata))
        github-logins (sort (distinct (mapcat gh/collaborator-logins github-urls)))]
    (doall (map #(if-not (md/person-metadata-by-github-login %)
                   (println "⚠️ GitHub login" % "doesn't have any metadata."))
                github-logins))))

(defn- check-github-orgs
  []
  (let [programs-with-invalid-github-orgs (filter #(nil? (gh/org (:github-org %))) (remove #(nil? (:github-org %)) (md/programs-metadata)))]
    (if (pos? (count programs-with-invalid-github-orgs)) (ec/set-error))
    (doall (map #(println "❌ Program" (:program-name %) "has an invalid GitHub org:" (:github-org %)) programs-with-invalid-github-orgs))))

(defn- check-github-repos
  []
  (let [github-repo-urls       (set (map str/lower-case (remove str/blank? (mapcat #(gh/repos-urls (:github-url %)) (md/programs-metadata)))))
        metadata-repo-urls     (set (map str/lower-case (remove str/blank? (mapcat :github-urls (md/activities-metadata)))))
        plus-pmc-repo-urls     (set (flatten (concat metadata-repo-urls (mapcat :pmc-github-urls (md/programs-metadata)))))
        repos-without-metadata (sort (set/difference github-repo-urls plus-pmc-repo-urls))
        metadatas-without-repo (sort (set/difference plus-pmc-repo-urls github-repo-urls))]
    (doall (map #(println "⚠️ GitHub repo" % "has no metadata.") repos-without-metadata))
    (doall (map #(println "⚠️ GitHub repo" % "has metadata, but does not exist in GitHub.") metadatas-without-repo))))

(defn- check-github-logins
  []
  (let [all-github-logins     (distinct (remove str/blank? (mapcat :github-logins (md/people-metadata))))
        invalid-github-logins (sort (filter #(nil? (gh/user %)) all-github-logins))]
    (doall (map #(println "ℹ️ GitHub username" % "is invalid (note: may be preserved for historical purposes).") invalid-github-logins))))

(defn- check-bitergia-projects
  []
  (let [activity-names                   (set (map :activity-name
                                                   (remove #(and (nil? (seq (:github-repos           %)))
                                                                 (nil? (seq (:mailing-list-addresses %)))
                                                                 (nil? (seq (:confluence-space-keys  %))))
                                                           (md/activities-metadata))))
        activities-missing-from-bitergia (sort (set/difference activity-names (bi/all-projects)))]
    (doall (map #(println "ℹ️ Activity" (activity-to-string (md/activity-metadata-by-name %)) "is missing from the Bitergia indexes (this is normal if there's been no activity in GitHub, Confluence, and the mailing lists yet).")
                activities-missing-from-bitergia))))

(defn check-remote
  "Performs checks that require API calls out to GitHub, JIRA, Bitergia, etc. (which may be rate limited)."
  []
  ; DEPRECATED - FINOS doesn't use repo admins anymore
  ; (check-repo-admins)
  (check-metadata-for-collaborators)
  (check-github-orgs)
  (check-github-repos)
  (check-github-logins)
  (check-bitergia-projects))




; Convenience fn for performing all checks


(defn check
  "Performs comprehensive checks, including API calls out to GitHub, JIRA, and Bitergia (which may be rate limited)."
  []
  (check-local)
  (check-remote))
