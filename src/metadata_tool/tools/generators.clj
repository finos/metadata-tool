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
  (:require [clojure.string                   :as s]
            [clojure.set                      :as set]
            [clojure.pprint                   :as pp]
            [clojure.tools.logging            :as log]
            [clojure.java.io                  :as io]
            [clojure.data.csv                 :as csv]
            [mount.core                       :as mnt :refer [defstate]]
            [cheshire.core                    :as ch]
            [metadata-tool.tools.parsers      :as psrs]
            [metadata-tool.config             :as cfg]
            [metadata-tool.template           :as tem]
            [metadata-tool.sources.confluence :as cfl]
            [metadata-tool.sources.github     :as gh]
            [metadata-tool.sources.bitergia   :as bi]
            [metadata-tool.sources.schemas    :as sch]
            [metadata-tool.sources.metadata   :as md]))

(defn gen-clabot-whitelist
  []
  (println (tem/render "clabot-whitelist.ftl"
                        { :github-ids (sort (mapcat :github-logins (md/people-with-clas))) })))

(defn gen-bitergia-affiliation-data
  []
  (println (tem/render "bitergia-affiliations.ftl"
                       { :people (md/people-metadata-with-organizations) })))

(defn gen-bitergia-organization-data
  []
  (println (tem/render "bitergia-organizations.ftl"
                       { :organizations (md/organizations-metadata) })))

(defn gen-bitergia-project-data
  []
  (println (tem/render "bitergia-projects.ftl"
                       { :programs   (md/programs-metadata)
                         :activities (md/activities-metadata) })))


(defn- build-github-repo-data
  [repo-url]
  (if-let [repo (gh/repo repo-url)]
    (let [collaborators (gh/collaborators repo-url)
          languages     (gh/languages     repo-url)]
      {
        :name          (or (:name        repo) "")
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
        :languages     languages
      })
    (log/warn "Unable to retrieve GitHub repository metadata for" repo-url)))

(defn- accumulate-github-stats
  [github-repos]
  (if-not (empty? github-repos)
    {
      :heat          (apply +                      (map :heat          github-repos))
      :watchers      (apply +                      (map :watchers      github-repos))   ; ####TODO: Fix double counting
      :size          (apply +                      (map :size          github-repos))
      :collaborators (apply +                      (map :collaborators github-repos))   ; ####TODO: Fix double counting
      :stars         (apply +                      (map :stars         github-repos))   ; ####TODO: Fix double counting
      :forks         (apply +                      (map :forks         github-repos))
      :open-issues   (apply +                      (map :open-issues   github-repos))
      :languages     (apply (partial merge-with +) (map :languages     github-repos))
    }))

(defn gen-catalogue-data
  []
  (let [activities-data (for [program-metadata  (md/programs-metadata)
                              activity-metadata (:activities program-metadata)]
                          (let [github-repos (remove nil? (map build-github-repo-data (:github-urls activity-metadata)))]
                            (assoc activity-metadata
                                   :program-name            (:program-name           program-metadata)
                                   :program-short-name      (:program-short-name     program-metadata)
                                   :program-home-page       (:url (:confluence-space program-metadata))
                                   :github-repos            github-repos
                                   :cumulative-github-stats (accumulate-github-stats github-repos))))]
    (println (tem/render "catalogue.ftl"
                         { :activities activities-data
                           :all-tags   (md/all-activity-tags) }))))

(defn gen-meeting-roster-data
  []
  (let [roster-data
        (remove nil? (flatten
          (for [program-metadata (md/programs-metadata)
                activity-metadata (:activities program-metadata)]
            (let [program-name (:program-name program-metadata)
                  activity-name (:activity-name activity-metadata)
                  page-url  (:confluence-page activity-metadata)]
              ; (println (str "Parsing " program-name " + " activity-name + " + " page-url))
              (if-let [page-url  (:confluence-page activity-metadata)]
                (psrs/meetings-rosters
                  program-name
                  activity-name
                  page-url))))))]
      (pp/pprint roster-data)
      (psrs/roster-to-csv roster-data)))
