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
(ns metadata-tool.tools.generators
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
            [metadata-tool.sources.metadata :as md]
            ))

(defn gen-clabot-whitelist
  []
  (println (str "[\n  "
                (s/join ",\n  " (map ch/generate-string (sort (mapcat :github-user-ids (md/people-with-clas)))))   ; Use Cheshire here to ensure JSON escaping
                "\n]")))






(comment
; ---- PRIVATE UTILITY FNs ----
(defn- print-project-leads
  [prj]
  (let [project-name             (key prj)
        repos                    (val prj)
        project-state            (u/project-state prj)
        repo-names               (sort (map #(get % "repositoryName") repos))
        project-leads-github-ids (sort (distinct (mapcat gh/project-lead-names repo-names)))
        project-leads-metadatas  (remove nil? (map #(let [result (md/user-metadata-by-github-id %)]
                                                    (if (nil? result)
                                                      (u/println-stderr "⚠️ Warning: unable to find user metadata for GitHub id" %)
                                                      result))
                                                   project-leads-github-ids))]
    (println (str "\n" project-name " (" project-state ")\n"
                  (s/join "\n" (map #(str "    " (u/user-as-string %)) project-leads-metadatas))))
    nil))

(defn- print-project-repos-and-team
  [prj]
  (let [project-name             (key prj)
        repos                    (val prj)
        project-state            (u/project-state prj)
        repo-names               (sort (map #(get % "repositoryName") repos))
        project-leads-github-ids (sort (distinct (mapcat gh/project-lead-names repo-names)))
        project-leads-metadatas  (remove nil? (map #(let [result (md/user-metadata-by-github-id %)]
                                                    (if (nil? result)
                                                      (u/println-stderr "⚠️ Warning: unable to find user metadata for GitHub id" %)
                                                      result))
                                                   project-leads-github-ids))
        project-team-github-ids  (sort (remove (partial u/contains-val? (concat project-leads-github-ids ["ssf-admin" "ssf-chatops-bot"]))
                                               (distinct (mapcat gh/collaborator-names repo-names))))
        project-team-metadatas   (remove nil? (map #(let [result (md/user-metadata-by-github-id %)]
                                                    (if (nil? result)
                                                      (u/println-stderr "⚠️ Warning: unable to find user metadata for GitHub id" %)
                                                      result))
                                                   project-team-github-ids))]
    (println (str "\n" project-name " (" project-state ")\n"
                  "\tRepositories:\n\t\t"       (s/join "\n\t\t" repo-names) "\n"
                  "\tProject Leads:\n\t\t"      (s/join "\n\t\t" (map #(u/user-as-string %) project-leads-metadatas)) "\n"
                  "\tProject Committers:\n\t\t" (s/join "\n\t\t" (map #(u/user-as-string %) project-team-metadatas))))
    nil))

(defn- repo-project-leads
  [repo-name]
  (let [project-leads          (gh/project-lead-names repo-name)
        project-leads-metadata (remove nil? (map #(let [result (md/user-metadata-by-github-id %)]
                                                    (if (nil? result)
                                                      (u/println-stderr "⚠️ Warning: unable to find metadata for user with GitHub id" %)
                                                      result))
                                                 project-leads))]
    (s/join "\n" (map #(str "    " (u/user-as-string %)) project-leads-metadata))))

(defn- gen-bitergia-affiliation
  [affiliation]
  (when affiliation
    (if-let [organization-metadata (md/organization-metadata (get affiliation "organizationId"))]
      (do
        (println "    - organization:" (get organization-metadata "name"))
        (if-let [start-date (get affiliation "startDate")]
          (println "      start:" start-date))
        (if-let [end-date (get affiliation "endDate")]
          (println "      end:" end-date)))
      (u/println-stderr "⚠️ Warning: unable to find metadata for organization with id" (get affiliation "organizationId")))))

(defn- gen-bitergia-user-profile
  [^java.io.File user-file]
  (println "\n- profile:")
  (let [json (ch/parse-string (slurp user-file))]
    (if-let [full-name (get json "fullName")]
      (if (not (s/blank? full-name))
        (println "    name:" full-name)))
    (if-let [is-bot (get json "isBot")]
      (println "    is_bot:" is-bot))
    (if-let [affiliations (get json "affiliations")]
      (when (seq affiliations)
        (println "  enrollments:")
        (doall (map gen-bitergia-affiliation affiliations))))
    (if-let [github-user-ids (get json "gitHubUserIds")]
      (when (seq github-user-ids)
        (println "  github:")
        (doall (map #(println "    -" %) github-user-ids))))
    (if-let [confluence-user-names (get json "confluenceUsernames")]
      (when (seq confluence-user-names)
        (println "  confluence:")
        (doall (map #(println "    -" %) confluence-user-names))))
    (if-let [email-addresses (get json "emailAddresses")]
      (when (seq email-addresses)
        (println "  email:")
        (doall (map #(println "    -" %) email-addresses))))))

(defn- gen-bitergia-project
  [project]
  (let [project-name (key project)
        repo-names   (map #(get % "repositoryName") (val project))]
    (str "  \"" project-name "\" : {\n"
         "    \"git\"    : [\n" (s/join ",\n"
         (map #(str "                 \"https://github.com/symphonyoss/" % ".git\"") repo-names))
         "\n               ],\n"
         "    \"github\" : [\n" (s/join ",\n"
         (map #(str "                 \"https://github.com/symphonyoss/" % "\"") repo-names))
         "\n               ],\n"
         "    \"meta\"   : { \"title\" : \"" project-name "\" }\n"
         "  }")))

(defn- gen-project-metadata-dir
  [repo-name]
  (println "Generating metadata for missing repo" repo-name)
  (let [metadata-folder (io/file md/project-metadata-directory repo-name)
        metadata-file   (io/file metadata-folder md/project-filename)]
    (.mkdirs metadata-folder)
    (spit metadata-file (s/join "\n"
      [    "{"
           "  \"metadataVersion\"  : \"2.0.0\","
           "  \"contribJiraKey\"   : \"CONTRIB-9999\","
      (str "  \"repositoryName\"   : \"" repo-name "\",")
           "  \"projectState\"     : \"INCUBATING\","
      (str "  \"projectName\"      : \"" repo-name "\",")
           "  \"contributionDate\" : \"1901-01-01\""
           "}"
           "" ]))))

(defn- gen-bitergia-affiliation-data
  []
  (println "# ⚠️ BITERGIA FOLKS: DO NOT EDIT THIS FILE!! ⚠️ ")
  (println "# We generate this file from a source data set, and your changes WILL BE OVERWRITTEN AND LOST!")
  (println "# If you need to make changes, please create an issue and we'll add your changes to the source data set.")
  (doall (map gen-bitergia-user-profile md/user-metadata-files))
  (println))

(defn- gen-bitergia-project-file
  []
  (let [projects (u/all-projects-including-placeholders)]
    (println "{")
    (println "  \"Wiki\" : {")
    (println "    \"confluence\" : [ \"https://symphonyoss.atlassian.net/wiki/\" ],")
    (println "    \"meta\"       : { \"title\" : \"Wiki\"}")
    (println "  },")
    (println "  \"Dev List\" : {")
    (println "    \"mbox\"       : [ \"dev /home/bitergia/mboxes/barnowl_symphony_dev\" ],")
    (println "    \"meta\"       : { \"title\" : \"Dev List\"}")
    (println "  },")
    (println "  \"WG-DW\" : {")
    (println "    \"confluence\" : [ \"WGDWAPI\" ],")
    (println "    \"mbox\"       : [ \"wg-desktop-wrapper /home/bitergia/mboxes/barnowl_symphony_wg-desktop-wrapper\" ],")
    (println "    \"meta\"       : { \"title\" : \"WG-DW\"}")
    (println "  },")
    (println "  \"WG-API\" : {")
    (println "    \"confluence\" : [ \"WGA\" ],")
    (println "    \"mbox\"       : [ \"wg-api /home/bitergia/mboxes/barnowl_symphony_wg-api\" ],")
    (println "    \"meta\"       : { \"title\" : \"WG-API\"}")
    (println "  },")
    (println "  \"WG-FOS\" : {")
    (println "    \"confluence\" : [ \"WGFOS\" ],")
    (println "    \"mbox\"       : [ \"wg-financial-objects /home/bitergia/mboxes/barnowl_symphony_wg-financial-objects\" ],")
    (println "    \"meta\"       : { \"title\" : \"WG-FOS\"}")
    (println "  },")
    (println "  \"ESCo\" : {")
    (println "    \"confluence\" : [ \"ESCo\" ],")
    (println "    \"meta\"       : { \"title\" : \"ESCo\"}")
    (println "  },")
    (println "  \"Foundation\" : {")
    (println "    \"confluence\" : [ \"FM\" ],")
    (println "    \"meta\"       : { \"title\" : \"Foundation\"}")
    (println "  },")
    (println (s/join ",\n" (map gen-bitergia-project projects)))
    (println "}")))

(defn- gen-project-metadata
  []
  (let [repos                 (set (gh/repo-names))
        project-metadata-dirs (set (map #(.getName ^java.io.File %) md/project-metadata-directories))
        missing-metadata      (sort (set/difference repos project-metadata-dirs))
        redundant-metadata    (sort (set/difference project-metadata-dirs repos))]
    (doall (map gen-project-metadata-dir missing-metadata))
    (doall (map #(println "⚠️ Warning: project metadata directory" % "is redundant (no repo has that name).") redundant-metadata))))

(defn- project-with-langs
  "Accept a project object in a sequence format (array of 2, first is key, second is value) and returns an enriched version of it (name, repos, state, jira and langs attributes)"
  [project-pair]
  (let [project-name  (nth project-pair 0)
        repositories  (nth project-pair 1)
        project-langs (apply merge-with + (u/get-project-langs project-pair))
        project-stats (apply merge-with + (u/get-project-stats project-pair))
        with-title    (assoc project-stats :name project-name)
        with-repos    (assoc with-title :repos repositories)
        with-state    (assoc with-repos :projectState (get (first repositories) "projectState"))
        with-langs    (assoc with-state :languages project-langs)
        with-jira     (assoc with-langs :contribJiraKey (get (first repositories) "contribJiraKey"))]
    with-jira))

(defn- gen-project-meta-for-website
  []
  (let [repo-files       md/project-metadata-files
        repo-jsons       (map #(ch/parse-string (slurp %)) repo-files)
        repo-filtered    (filter #(u/is-project %) repo-jsons)
        projects-pairs   (seq (group-by u/get-project-name repo-filtered))
        projects-norm    (map project-with-langs projects-pairs)
        projects-ordered (reverse (sort-by :hotness projects-norm))]
    (println (ch/generate-string projects-ordered))))

(defn- is-date-in-range
  "True if:
  - start/end dates are set and input-date is within ranges
  - start date are set and input-date is after that
  - end date are set and input-date is before that
  Fails if input-date is not valid"
  [input-date start-date end-date]
    (if (and
          start-date
          end-date
          (tm/before? input-date (tf/parse u/date-formatter start-date))
          (tm/after? input-date (tf/parse u/date-formatter end-date))) false
        (if (and
          start-date
          (tm/before? input-date (tf/parse u/date-formatter start-date))) false
            (if (and
              end-date
              (tm/after? input-date (tf/parse u/date-formatter end-date))) false true))))

(defn- is-date-obj-in-range
  [input-date obj]
  (let [start-date (get obj "startDate")
        end-date   (get obj "endDate")]
    (is-date-in-range input-date start-date end-date)))

(defn- is-user-approved-by-org
  "True if:
  - Field 'approvedUsers' doesn't exist in organization-metadata
  - Field 'approvedUsers' exists and contains an entry with matching 'gitHubUserId'
  and start/end dates are before/after the current one"
  [organization-metadata github-user-id]
  (if-let [approved-users (get organization-metadata "approvedUsers")]
    (if-let [approved-user (filter #(= (get % "gitHubUserId") github-user-id) approved-users)]
      (is-date-obj-in-range (tm/now) approved-user)
      false)
    true))

(defn- has-cla
  "True if:
  - user metadata defines a field 'hasICLA' = true
  - user metadata defines a field 'affiliations', including
    - a startDate/endDate that are lesser/greater than the current date
    - an organization whose metadata defines
      - a field 'hasCCLA' = true
      - (optional) field 'approvedUsers' of organisation metadata is validated"
  [user-json]
  (let [has-icla           (get user-json "hasICLA")
        current-date       (tm/now)
        github-user-ids    (get user-json "gitHubUserIds")
        valid-affiliations (filter #(is-date-obj-in-range current-date %) (get user-json "affiliations"))
        ccla-affiliations  (filter #(get % "hasCCLA") (map #(md/organization-metadata (get % "organizationId")) valid-affiliations))
        has-ccla           (some identity (for [affiliation    ccla-affiliations
                                                github-user-id github-user-ids]
                                            (is-user-approved-by-org affiliation github-user-id)))]
    (or has-icla has-ccla)))   ; TODO: check with Aaron whether an ICLA "trumps" a CCLA with a schedule B that does not include that user


)
