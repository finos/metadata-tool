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
(ns metadata-tool.tools.generators
  (:require [clojure.string                 :as s]
            [clojure.set                    :as set]
            [clojure.pprint                 :as pp]
            [clojure.tools.logging          :as log]
            [clojure.java.io                :as io]
            [mount.core                     :as mnt :refer [defstate]]
            [cheshire.core                  :as ch]
;            [clj-time.core                  :as tm]
;            [clj-time.format                :as tf]
;            [metadata-tool.utils            :as u]
            [metadata-tool.config           :as cfg]
            [metadata-tool.template         :as tem]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.schemas  :as sch]
            [metadata-tool.sources.metadata :as md]
            ))

(defn gen-clabot-whitelist
  []
  (println (tem/render "clabot-whitelist.ftl"
                        { :github-ids (sort (mapcat :github-user-ids (md/people-with-clas))) }))
  (flush))

(defn gen-bitergia-affiliation-data
  []
  (println (tem/render "bitergia-affiliations.ftl"
                       { :people (md/people-metadata-with-organizations) }))
  (flush))

(defn gen-bitergia-project-data
  []
  (println (tem/render "bitergia-projects.ftl"
                       { :activities (md/activities-metadata) }))
  (flush))


(defn- build-github-repo-data
  [repo-url]
  (if-not (s/blank? repo-url)
    (let [repo          (gh/repo          repo-url)
          collaborators (gh/collaborators repo-url)
          languages     (gh/languages     repo-url)]
    {
      :name          (get repo :name              "")
      :description   (get repo :description       "")
      :url           repo-url
      :hotness       (+ (* (count collaborators)  5)
                        (* (get repo :forks    0) 4)
                        (* (get repo :stars    0) 1)
                        (* (get repo :watchers 0) 1))
      :watchers      (get repo :watchers-count    0)
      :size          (get repo :size              0)
      :collaborators (count collaborators)
      :stars         (get repo :stargazers-count  0)
      :forks         (get repo :forks-count       0)
      :open-issues   (get repo :open-issues-count 0)
      :languages     languages
    })))

;####TODO: THIS ISN'T RIGHT - IT DOUBLE COUNTS!
(defn- accumulate-github-stats
  [github-repos]
  (if-not (empty? github-repos)
    {
      :hotness       (apply +                      (map :hotness       github-repos))
      :watchers      (apply +                      (map :watchers      github-repos))
      :size          (apply +                      (map :size          github-repos))
      :collaborators (apply +                      (map :collaborators github-repos))
      :stars         (apply +                      (map :stars         github-repos))
      :forks         (apply +                      (map :forks         github-repos))
      :open-issues   (apply +                      (map :open-issues   github-repos))
      :languages     (apply (partial merge-with +) (map :languages     github-repos))
    }))

(defn gen-catalogue-data
  []
  (let [activities-data (for [program-metadata  (md/programs-metadata)
                              activity-metadata (:activities program-metadata)]
                          (let [github-repos (map build-github-repo-data (:github-urls activity-metadata))]
                            (assoc activity-metadata
                                   :program-name            (:program-name program-metadata)
                                   :github-repos            github-repos
                                   :cumulative-github-stats (accumulate-github-stats github-repos))))]
    (println (tem/render "catalogue.ftl"
                         { :activities activities-data
                           :all-tags   (md/all-activity-tags) })))
  (flush))

