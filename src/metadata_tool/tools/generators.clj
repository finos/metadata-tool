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
(ns metadata-tool.tools.generators
  (:require [clojure.tools.logging            :as log]
            [clojure.string                   :as s]
            [clojure.pprint                   :as pp]
            [clojure.set                      :as set]
            [clojure.java.io                  :as io]
            [metadata-tool.sources.bitergia   :as bi]
            [metadata-tool.config             :as cfg]
            [clj-yaml.core                    :as yaml]
            [metadata-tool.tools.parsers      :as psrs]
            [metadata-tool.template           :as tem]
            [metadata-tool.sources.github     :as gh]
            [metadata-tool.sources.metadata   :as md]))

(def crunchbase-prefix "https://www.crunchbase.com/organization/")

(defn invite-clas-to-finos-org
  []
  (let [member-ids (set (remove nil?
                                (map #(first (:github-logins %))
                                     (remove #(:is-bot %) (md/people-with-clas)))))
        all-ids    (set (remove nil?
                                (map #(first (:github-logins %))
                                     (md/people-with-clas))))
        gh-members (set (map :login (gh/org-members "finos")))
        pending    (set (map #(get % "login")  (gh/pending-invitations "finos")))
        skip-email (set (:skip-invites cfg/config))
        skip-ppl   (map #(md/person-metadata-by-email-address-fn %) skip-email)
        skip-users (set (flatten (map #(first (:github-logins %)) skip-ppl)))
        to-flag    (set/difference (set/union pending gh-members) all-ids)
        to-invite  (set/difference member-ids (set/union pending gh-members skip-users))]
        ;; Extract emails
        ;; invitee-email  (map #(first (:email-addresses (md/person-metadata-by-github-login (s/trim %)))) to-invite)]
    (println "Inviting CLA signed GitHub users to github.com/orgs/finos/people")
    (println "Invitations to skip:" (count skip-users))
    (println "Pending invitation:" (count pending))
    (println "FINOS members:" (count gh-members))
    (println "CLA covered people:" (count member-ids))
    (println "All CLAs on file (includes bots):" (count all-ids))
    (println "Members with no CLA -" (count to-flag) "-" to-flag)
    (println (str "To invite: (" (count to-invite) ") " to-invite))
    (println "Invitation enabled?" (:enable-auto-invite cfg/config))
    ;; Extract emails
    ;; (println "Invite emails...")
    ;; (doall (for [email invitee-email] (println email ",")))
    (if (:enable-auto-invite cfg/config)
      (doall (for [user to-invite] (gh/invite-member "finos" user)))
      (println "Skipping invitation sending by configuration"))))

(defn gen-clabot-whitelist
  []
  (println (tem/render "clabot-whitelist.ftl"
                       {:github-ids (sort (mapcat :github-logins (md/people-with-clas)))
                        :email-domains 
                          (sort
                            (mapcat :domains 
                              (filter :cla-email-whitelist
                                      (md/organizations-metadata))))})))
(defn gen-clabot-ids-whitelist
  []
  (let [names (sort (mapcat :github-logins (md/people-with-clas)))
        ids (distinct (remove nil? (map #(:id (gh/user %)) names)))
        as-strings (map #(str "\"" % "\"") ids)
        as-string (str "[" (s/join "," as-strings) "]")]
    (println as-string)))

(defn add-gh-id-email
  [person]
  (let [emails    (remove nil? (:email-addresses person))
        usernames (remove nil? (:github-logins person))
        gh-emails (map #(str % "@users.noreply.github.com") usernames)
        user-ids  (map #(assoc {}
                               :user %
                               :id   (gh/user-id %)) usernames)
        user-ids-nn (remove #(s/blank? (str (:id %))) user-ids)
        email-ids (map #(str (:id %) "+" (:user %) "@users.noreply.github.com") user-ids-nn)
        all-emails (apply sorted-set (set (concat emails gh-emails email-ids)))]
    (assoc person :email-addresses all-emails)))

(defn gen-bitergia-affiliation-data
  []
  (println (tem/render "bitergia-affiliations.ftl"
                       {:people (map #(add-gh-id-email %) 
                                     (md/people-metadata-with-organizations))})))

(defn gen-bitergia-organization-data
  []
  (println (tem/render "bitergia-organizations.ftl"
                       {:organizations (md/organizations-metadata)})))

(defn gen-bitergia-project-data
  []
  (println (tem/render "bitergia-projects.ftl"
                       {:programs   (md/programs-metadata)
                        :activities (md/activities-metadata-after-disband)})))

(defn- build-github-repo-data
  [repo-url]
  (if-let [repo (gh/repo repo-url)]
    (let [collaborators (gh/collaborators repo-url)
          languages     (gh/languages     repo-url)]
      {:name          (or (:name        repo) "")
       :description   (or (:description repo) "")
       :url           repo-url
       :heat          (+ (* (count collaborators)           4)
                         (* (or (:forks_count      repo) 0) 5)
                         (* (or (:stargazers_count repo) 0) 1)
                         (* (or (:watchers_count   repo) 0) 1))
       :watchers      (or (:watchers_count    repo) 0)
       :size          (or (:size              repo) 0)
       :collaborators (count collaborators)
       :stars         (or (:stargazers_count  repo) 0)
       :forks         (or (:forks_count       repo) 0)
       :open-issues   (or (:open_issues_count repo) 0)
       :languages     languages})
    (log/warn "Unable to retrieve GitHub repository metadata for" repo-url)))

(defn- accumulate-github-stats
  [github-repos]
  (if-not (empty? github-repos)
    {:heat          (apply +                      (map :heat          github-repos))
     :watchers      (apply +                      (map :watchers      github-repos))   ; ####TODO: Fix double counting
     :size          (apply +                      (map :size          github-repos))
     :collaborators (apply +                      (map :collaborators github-repos))   ; ####TODO: Fix double counting
     :stars         (apply +                      (map :stars         github-repos))   ; ####TODO: Fix double counting
     :forks         (apply +                      (map :forks         github-repos))
     :open-issues   (apply +                      (map :open-issues   github-repos))
     :languages     (apply (partial merge-with +) (map :languages     github-repos))}))

(defn- get-home-page
  [program]
  (if (some? (:confluence-space program))
    (:url (:confluence-space program))
    "https://finos.org"))

(defn gen-catalogue-data
  []
  (let [toplevel-program (md/program-metadata "toplevel")
        activities-data  (for [program-metadata  (md/programs-metadata)
                               activity-metadata (:activities program-metadata)]
                           (let [is-toplevel  (:disbanded program-metadata)
                                 program (if is-toplevel toplevel-program program-metadata)
                                 github-repos (keep build-github-repo-data (:github-urls activity-metadata))]
                             (assoc activity-metadata
                                    :program-name            (:program-name           program)
                                    :program-short-name      (:program-short-name     program)
                                    :program-home-page       (get-home-page program)
                                    :github-repos            github-repos
                                    :cumulative-github-stats (accumulate-github-stats github-repos))))]
    (println (tem/render "catalogue.ftl"
                         {:activities activities-data
                          :all-tags   (md/all-activity-tags)}))))

(defn- gen-activity-roster
  [program-name activity-metadata]
  (let [activity-name (:activity-name activity-metadata)]
    (if-let [page-url (:confluence-page activity-metadata)]
      (psrs/meetings-rosters
       program-name
       activity-name
       (:type activity-metadata)
       page-url))))

(defn- gen-program-roster
  [program-metadata]
  (let [program-name (:program-short-name program-metadata)] [(println (str "Generating meeting attendance for program " program-name))
                                                              (if-let [pmc-confluence-page (:pmc-confluence-page program-metadata)]
                                                                (psrs/meetings-rosters
                                                                 program-name
                                                                 (str program-name " PMC")
                                                                 "PMC"
                                                                 pmc-confluence-page))
                                                              (map #(gen-activity-roster program-name %) (:activities program-metadata))]))

(defn gen-meeting-roster-data
  []
  (->> (md/programs-metadata)
       (keep gen-program-roster)
       flatten
       psrs/roster-to-csv))

(defn gen-meeting-github-roster-data
  []
  (if (psrs/has-meeting-config)
    (let [json    (psrs/get-json "./meeting-attendance.json")
          data    (psrs/string-keys-to-symbols json)
          date    (first (s/split (:date data) #"T"))
          people  (map #(assoc
                         (md/person-metadata-by-github-login (s/trim %))
                         :gh-username %)
                       ; using assignees - deprecated
                       ;  (s/split (:attendants data) #","))
                       ; using commenters
                       (gh/issue-commenters
                        (:issueNumber data)
                        (:org data)
                        (:repo data)))
          attendants (filter #(not (nil? (:person-id %))) people)
          not-on-file (map :gh-username (filter #(nil? (:person-id %)) people))
          not-on-file-ids (map #(str "@" (s/trim %)) not-on-file)
          project (first
                   (filter #(psrs/match-project % data)
                           (md/projects-metadata)))
          roster  (remove nil? (map #(psrs/single-attendance % project date) attendants))
          delta   (psrs/get-csv-delta roster)
          exist   (first delta)
          new     (second delta)
          action  (:action data)]
      (if (not-empty not-on-file-ids)
        (println "WARN - Couldn't find the following GitHub usernames on file:" not-on-file-ids))
      (if (not-empty not-on-file-ids)
        (with-open [writer (psrs/get-writer "./github-finos-meetings-unknowns.txt")]
          (.write writer (s/join ", " not-on-file-ids))))
      (if (= action "add")
        (with-open [writer (psrs/get-writer "./github-finos-meetings-add.csv")]
          (psrs/write-csv writer new))
        (with-open [reader (psrs/get-reader "./github-finos-meetings.csv")
                    writer (psrs/get-writer "./github-finos-meetings-remove.csv")]
          (->> (psrs/read-csv reader)
               (psrs/remove-existing-entries exist)
               (psrs/write-csv writer)))))
    (println "ERROR - Cannot find meeting-attendance.json or github-finos-meetings.csv files")))

(defn- get-project-logo
  [project]
    (if-let [logo (:logo project)]
      logo
      "finos.svg"))

(defn- get-org-logo
  [org]
    (if-let [logo (:logo org)]
      logo
      "finos.svg"))

(defn- landscape-format
  "Returns project metadata in landscape format"
  [project]
  (if-let [cat  (:category project)]
    (let [repos (:github-urls project)
          addit (map #(assoc {} :repo_url %) (rest repos))
          raw   (assoc {}
                        :item []
                        :name (:activity-name project)
                        :homepage_url (or (:homepage project) (first repos))
                        :repo_url (first repos)
                        :logo (get-project-logo project)
                        ; :types (:taxonomy-types project)
                        :category cat
                        :subcategory (:sub-category project)
                        :organization {:name "FINOS"})
          final (if (empty? addit) raw (assoc raw :additional_repos addit))]
      final)))

(defn- clean-item
  ""
  [item]
  (dissoc (dissoc item :subcategory) :category))

(defn- clean-items
  ""
  [items]
  (map #(clean-item %) items))

(defn- get-name
""
[name]
(if (nil? name) "undefined" name))

(defn- get-subcategories
  ""
  [category]
  (let [sub-cats (group-by :subcategory category)]
    (map #(assoc {} 
                 :subcategory []
                 :name (get-name (first %))
                 :items (clean-items (second %))) sub-cats)))

(defn- group-by-sub
  ""
  [categories]
  (map #(assoc {} 
               :category []
               :name (first %)
               :subcategories (get-subcategories (second %)))
       (seq categories)))

(defn format-member
  [org]
  (let [item {:item []
              :name         (:organization-name org)
              :homepage_url (str "https://www." (first (:domains org)))
              :logo         (get-org-logo org)
              :crunchbase   (str crunchbase-prefix (:crunchbase org))}]
  (if (contains? org :stock-ticker)
    (assoc item :stock_ticker (:stock-ticker org))
    item)))

(defn format-members
  [orgs tier]
  {:category []
   :name (str "FINOS " tier " Member")
   :subcategories [{:subcategory []
                    :name "General"
                    :items (map #(format-member %) orgs)}]})

(def finos-cat
  {:category []
   :name "FINOS"
   :subcategories [{:subcategory []
                    :name "General"
                    :items [{:item []
                             :name         "FINOS"
                             :homepage_url "https://www.finos.org"
                             :logo         "finos.svg"
                             :crunchbase   "https://www.crunchbase.com/organization/symphony-software-foundation"}]}]})

(defn gen-project-landscape
  "Generates a landscape.yml, using Programs as categories and tags as subcategories"
  []
  (let [raw (md/activities-metadata)
        projects           (remove nil? (map #(landscape-format %) raw))
        orgs               (md/organizations-metadata)
        plat-members       (format-members (filter #(= "platinum" (:membership %)) orgs) "Platinum")
        gold-members       (format-members (filter #(= "gold" (:membership %)) orgs) "Gold")
        silv-members       (format-members (filter #(= "silver" (:membership %)) orgs) "Silver")
        by-category        (group-by :category projects)
        by-sub-categories  (group-by-sub by-category)
        add-static-entries (concat by-sub-categories [finos-cat plat-members gold-members silv-members])]
    ; (pp/pprint (get-projects)))
    (with-open [w (io/writer "landscape.yml" :append true)]
      (.write w 
              (str 
               "# NOTE! This file is automatically generated by github.com/finos/metadata-tool AND pushed to GitHub; any manual change could be overridden at any time.\n\n"
              (s/replace 
               (yaml/generate-string {:landscape add-static-entries} :dumper-options {:flow-style :block})
               " []" ""))))))

(defn list-project-leads
  "Returns the list of projects and lead data"
  []
  (println (psrs/to-json (md/project-leads))))
