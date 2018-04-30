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
(ns metadata-tool.sources.bitergia
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.set           :as set]
            [mount.core            :as mnt :refer [defstate]]
            [qbits.spandex         :as es]
            [metadata-tool.config  :as cfg]))

(def ^:private bitergia-url                 "https://symphonyoss.biterg.io")   ; Note: no trailing / or spandex will assplode!
(def ^:private git-search-endpoint          "/data/git/_search")
(def ^:private github-search-endpoint       "/data/github_issues/_search")
(def ^:private wiki-search-endpoint         "/data/confluence/_search")
(def ^:private mailing-list-search-endpoint "/data/mbox/_search")

(def ^:private endpoints #{ git-search-endpoint
                            github-search-endpoint
                            wiki-search-endpoint
                            mailing-list-search-endpoint })

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

(defn- all-projects-for-endpoint
  "Returns the names of all known projects at the given endpoint."
  [endpoint]
  (if endpoint
    (set (map #(s/trim (:key %))
              (:buckets (:projects (:aggregations (:body (es/request client { :url    endpoint
                                                                              :method :get
                                                                              :body   query-all-projects } )))))))))

(defn all-projects
  "Returns the set of project names with activity tracked in Bitergia."
  []
  (set (mapcat all-projects-for-endpoint endpoints)))


(defn- recent-projects-query
  "Returns an ElasticSearch query for all recent projects (those that have had a commit in the last threshold-in-days)."
  [threshold-in-days]
  { :size 0
    :query {
      :constant_score {
         :filter {
            :range {
               :commit_date {
                  :gte (str "now-" threshold-in-days "d/d")
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

(defn- recently-active-projects-for-endpoint
  [endpoint threshold-in-days]
  (if endpoint
    (set
      (map #(s/trim (:key %))
           (:buckets
             (:projects
               (:aggregations
                 (:body (es/request client { :url    endpoint
                                             :method :get
                                             :body   (recent-projects-query threshold-in-days) } )))))))))

(defn recently-active-projects
  "Returns the set of recently active project names (those with activity in any data source in the last threshold-in-days)."
  [threshold-in-days]
  (set (mapcat #(recently-active-projects-for-endpoint % threshold-in-days) endpoints)))

(defn inactive-projects
  "Returns the set of inactive project names (those with no activity in any data source in the last threshold-in-days)."
  [threshold-in-days]
  (set/difference (all-projects) (recently-active-projects threshold-in-days)))


(defn- old-github-issues-query
  "Returns the ElasticSearch query for old GitHub issues or PRs."
  [threshold-in-days pr?]
  { :size 0
    :query {
      :constant_score {
         :filter {
            :bool {
              :must [ {
                  :range {
                     :time_open_days {
                        :gte (str threshold-in-days)
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

(defn projects-with-old-prs
  "Returns the set of project names with PRs older than threshold-in-days."
  [threshold-in-days]
  (set (map #(s/trim (:key %))
            (:buckets (:projects (:aggregations (:body (es/request client { :url    github-search-endpoint
                                                                            :method :get
                                                                            :body   (old-github-issues-query threshold-in-days true) } ))))))))

(defn projects-with-old-issues
  "Returns the set of project names with issues older than threshold-in-days."
  [threshold-in-days]
  (set (map #(s/trim (:key %))
            (:buckets (:projects (:aggregations (:body (es/request client { :url    github-search-endpoint
                                                                            :method :get
                                                                            :body   (old-github-issues-query threshold-in-days false) } ))))))))
