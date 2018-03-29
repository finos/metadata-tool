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
(ns metadata-tool.sources.bitergia
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.set           :as set]
            [mount.core            :as mnt :refer [defstate]]
            [qbits.spandex         :as es]
            [metadata-tool.config  :as cfg]))

(def ^:private bitergia-url           "https://symphonyoss.biterg.io")   ; Note: no trailing / or spandex will assplode!
(def ^:private git-search-endpoint    "/data/git/_search")
(def ^:private github-search-endpoint "/data/github_issues/_search")

; Tunables
(def inactive-project-days 180)   ; The age in days at which a project is considered "inactive"
(def old-pr-days           60)    ; The age in days at which a PR is considered "old"
(def old-issue-days        60)    ; The age in days at which an issue is considered "old"

(defstate client
          :start (es/client {:hosts       [bitergia-url]
                             :http-client {:basic-auth {:user (:username (:bitergia cfg/config)) :password (:password (:bitergia cfg/config))}}}))

(def ^:private query-all-projects
  "This ElasticSearch query returns all projects known to Bitergia."
  { :size 0
    :aggs {
      :projects {
        :terms {
          :field "project"
          :size  1000
        }
      }
    }
  })

(defn all-projects-git
  "Returns the set of projects with git activity tracked in Bitergia."
  []
  (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    git-search-endpoint
                                                                                :method :get
                                                                                :body   query-all-projects}))))))))

(defn all-projects-github
  "Returns the set of projects with github issue / PR activity tracked in Bitergia."
  []
  (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    github-search-endpoint
                                                                                :method :get
                                                                                :body   query-all-projects}))))))))

(defn all-projects
  "Returns the set of projects with git or GitHub issue activity tracked in Bitergia."
  []
  (set/union (all-projects-git) (all-projects-github)))


(def ^:private query-active-projects
  "This ElasticSearch query returns active projects (those that have had a commit in the last 90 days)."
  { :size 0
    :query {
      :constant_score {
         :filter {
            :range {
               :commit_date {
                  :gte (str "now-" inactive-project-days "d/d")
               }
            }
         }
      }
    }
    :aggs {
      :projects {
        :terms {
          :field "project"
          :size  1000
        }
        :aggs {
          :last_commit_date {
            :max {
              :field "commit_date"
            }
          }
        }
      }
    }
  })

(defn active-projects
  "Returns the set of active projects (those with git commit or GitHub activity in the last 90 days)."
  []
  (let [projects-with-git-activity    (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    git-search-endpoint
                                                                                                                    :method :get
                                                                                                                    :body   query-active-projects})))))))
        projects-with-github-activity (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    github-search-endpoint
                                                                                                                    :method :get
                                                                                                                    :body   query-active-projects})))))))]
    (set/union projects-with-git-activity projects-with-github-activity)))


(defn inactive-projects
  []
  (set/difference (all-projects) (active-projects)))


(defn- old-github-issues-query
  "Returns the ElasticSearch query for old GitHub issues or PRs."
  [age-in-days pr?]
  { :size 0
    :query {
      :constant_score {
         :filter {
            :bool {
              :must [ {
                  :range {
                     :time_open_days {
                        :gte (str age-in-days)
                     }
                  }
                }
                {
                  :term {
                     :pull_request pr?
                  }
                }
              ]
            }
         }
      }
    }
    :aggs {
      :projects {
        :terms {
          :field "project"
          :size  1000
        }
        :aggs {
          :last_commit_date {
            :max {
              :field "time_open_days"
            }
          }
        }
      }
    }
  })

(def ^:private query-projects-with-old-prs
  "This ElasticSearch query returns all projects that have old PRs."
  (old-github-issues-query old-pr-days true))

(defn projects-with-old-prs
  "Returns the set of projects with old PRs."
  []
  (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    github-search-endpoint
                                                                                :method :get
                                                                                :body   query-projects-with-old-prs}))))))))

(def ^:private query-projects-with-old-issues
  "This ElasticSearch query returns all projects that have old issues."
  (old-github-issues-query old-issue-days false))

(defn projects-with-old-issues
  "Returns the set of projects with old issues."
  []
  (set (map :key (:buckets (:projects (:aggregations (:body (es/request client {:url    github-search-endpoint
                                                                                :method :get
                                                                                :body   query-projects-with-old-issues}))))))))
