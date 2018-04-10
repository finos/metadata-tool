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
(ns metadata-tool.tools.reports
  (:require [clojure.string                 :as s]
            [clojure.tools.logging          :as log]
            [clojure.set                    :as set]
            [mount.core                     :as mnt :refer [defstate]]
            [clj-time.core                  :as tm]
            [clj-time.format                :as tf]
            [postal.core                    :as email]
            [metadata-tool.config           :as cfg]
            [metadata-tool.template         :as tem]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.metadata :as md]
            [metadata-tool.sources.bitergia :as bi]
            [metadata-tool.sources.joins    :as j]))

(defstate email-config
          :start (:email cfg/config))

(defstate from-address
          :start (:user email-config))

(defstate email-override
          :start (:email-override cfg/config))

(defstate test-email-address
          :start (:test-email-address email-config))

(defn- send-email
  ([to-address subject body] (send-email to-address from-address subject body))
  ([to-address from-address subject body]
    (let [to-address (if email-override
                       to-address
                       test-email-address)]
      (log/debug "Sending email to" to-address "with subject:" subject)
      (email/send-message email-config
                          { :from         from-address
                            :to           to-address
                            :subject      subject
                            :body         [{ :type    "text/html; charset=\"UTF-8\""
                                             :content body }] } ))))

(defn- send-email-to-pmc
  ([program-id subject body] (send-email-to-pmc program-id from-address subject body))
  ([program-id from-address subject body]
     (if-not (s/blank? program-id)
       (send-email (:pmc-mailing-list-address (md/program-metadata program-id))
                   from-address
                   subject
                   body))))

(defn email-inactive-projects-reports
  []
  (let [inactive-project-names                (bi/inactive-projects)
        inactive-unarchived-projects-metadata (group-by :program-id
                                                        (remove #(= "ARCHIVED" (:state %))
                                                                (remove nil?
                                                                        (map #(j/activity-with-team (md/activity-metadata-by-name %))
                                                                             inactive-project-names))))
        now-str                               (tf/unparse (tf/formatter "yyyy-MM-dd h:mmaa ZZZ") (tm/now))]
    (if (empty? inactive-unarchived-projects-metadata)
      (log/info "No inactive, non-archived projects found - skipping reports.")
      (do
        (log/info "Sending" (count (keys inactive-unarchived-projects-metadata)) "inactive, non-archived projects reports to PMCs...")
        (doall (map #(send-email-to-pmc (key %)
                                        (str "Inactive Projects, as at " now-str)
                                        (tem/render "email/inactive-projects-report.ftl"
                                                    { :inactive-projects (val %)
                                                      :inactive-days     bi/inactive-project-days
                                                      :now               now-str } ))
                    inactive-unarchived-projects-metadata))
        (log/info "Inactive, non-archived projects reports sent.")))))










(comment
(defn- render-user
  [user-json]
  (if user-json
    (let [full-name     (get user-json "fullName")
          email-address (let [first-email (first (get user-json "emailAddresses"))]
                          (when-not (s/ends-with? first-email "users.noreply.github.com")
                            first-email))]
      (if email-address
        (if full-name
          [:a {:href (str "mailto:\"" full-name "\" <" email-address ">")} full-name]
          [:a {:href (str "mailto:" email-address)} email-address])
        full-name))))

(defn- render-project-row
  [project]
  (let [project-name            (key project)
        repo-jsons              (val project)
        project-state           (s/capitalize (u/project-state project))
        repository-names        (map #(get % "repositoryName") repo-jsons)
        project-lead-github-ids (set (flatten (map gh/project-lead-names repository-names)))
        project-lead-jsons      (map md/user-metadata-by-github-id project-lead-github-ids)]
    [:tr
      [:td project-name]
      [:td project-state]
      [:td (mapcat #(vec [[:a {:href (str "https://github.com/symphonyoss/" %)} %] [:br]])
                   repository-names)]
      [:td (mapcat #(vec [(render-user %) [:br]]) project-lead-jsons)]]))

(defn email-inactive-projects-report
  []
  (let [inactive-projects (u/inactive-non-archived-projects)]
    (if (seq inactive-projects)
      (do
        (print "Preparing and sending inactive, non-archived projects report...")
        (flush)
        (let [now-str    (u/now-as-string)
              email-body (hic/html [:html
                                     [:head
                                       [:meta {:http-equiv "content-type" :content "text/html;charset=utf-8"}]
                                     ]
                                     [:body
                                       [:p [:b "Inactive Projects Report, as at " now-str]]
                                       [:p "Here are the currently inactive projects (defined as being those projects with no git commit or GitHub issue/PR activity in the last "
                                           bt/inactive-project-days
                                           " days), that are not in "
                                           [:a {:href "https://symphonyoss.atlassian.net/wiki/display/FM/Archived"} "Archived state"]
                                           ":"]
                                       [:p
                                         [:blockquote
                                           [:table {:width "600px" :border 1 :cellspacing 0}
                                             [:thead
                                               [:tr {:bgcolor "#CCCCCC"}
                                                 [:th "Project"] [:th "Lifecycle State"] [:th "Repositories"] [:th "Project Leads"]
                                               ]
                                             ]
                                             [:tbody
                                               (map render-project-row inactive-projects)
                                             ]
                                           ]
                                         ]
                                       ]
                                       [:p "To dig further into this data, please use the " [:a {:href "https://metrics.symphony.foundation/"} "metrics dashboard"] "."]
                                     ]
                                   ]
                         )]
          (send-email-to-esco (str "ESCo Report: Inactive Projects, as at " now-str)
                                   [{ :type    "text/html; charset=\"UTF-8\""
                                      :content email-body }]))
          (println " ...📧 sent to" esco-email-address))
      (println "No inactive, non-archived projects - skipping report."))))


(defn email-active-projects-with-unactioned-prs-report
  []
  (let [active-projects-with-unactioned-prs (u/active-projects-with-old-prs)]
    (if (seq active-projects-with-unactioned-prs)
      (do
        (print "Preparing and sending active projects with unactioned PRs report...")
        (flush)
        (let [now-str    (u/now-as-string)
              email-body (hic/html [:html
                                     [:head
                                       [:meta {:http-equiv "content-type" :content "text/html;charset=utf-8"}]
                                     ]
                                     [:body
                                       [:p [:b "Active Projects with Unactioned PRs Report, as at " now-str]]
                                       [:p "Here are the currently active projects (defined as being those projects with git commit or GitHub issue/PR activity in the last "
                                           bt/inactive-project-days
                                           " days) that have unactioned PRs (defined as PRs that have been open for more than "
                                           bt/old-pr-days
                                           " days):"]
                                       [:p
                                         [:blockquote
                                           [:table {:width "600px" :border 1 :cellspacing 0}
                                             [:thead
                                               [:tr {:bgcolor "#CCCCCC"}
                                                 [:th "Project"] [:th "Lifecycle State"] [:th "Repositories"] [:th "Project Leads"]
                                               ]
                                             ]
                                             [:tbody
                                               (map render-project-row active-projects-with-unactioned-prs)
                                             ]
                                           ]
                                         ]
                                       ]
                                       [:p "To dig further into this data, please use the " [:a {:href "https://metrics.symphony.foundation/app/kibana#/dashboard/GitHub-Pull-Requests-Delays"} "metrics dashboard"] "."]
                                     ]
                                   ]
                         )]
          (send-email-to-esco (str "ESCo Report: Active Projects with Unactioned PRs, as at " now-str)
                                   [{ :type    "text/html; charset=\"UTF-8\""
                                      :content email-body }]))
          (println " ...📧 sent to" esco-email-address))
      (println "No active projects with unactioned PRs - skipping report."))))


(defn email-active-projects-with-unactioned-issues-report
  []
  (let [active-projects-with-unactioned-issues (u/active-projects-with-old-issues)]
    (if (seq active-projects-with-unactioned-issues)
      (do
        (print "Preparing and sending active projects with unactioned issues report...")
        (flush)
        (let [now-str    (u/now-as-string)
              email-body (hic/html [:html
                                     [:head
                                       [:meta {:http-equiv "content-type" :content "text/html;charset=utf-8"}]
                                     ]
                                     [:body
                                       [:p [:b "Active Projects with Unactioned Issues Report, as at " now-str]]
                                       [:p "Here are the currently active projects (defined as being those projects with git commit or GitHub issue/PR activity in the last "
                                           bt/inactive-project-days
                                           " days) that have unactioned issues (defined as issues that have been open for more than "
                                           bt/old-issue-days
                                           " days):"]
                                       [:p
                                         [:blockquote
                                           [:table {:width "600px" :border 1 :cellspacing 0}
                                             [:thead
                                               [:tr {:bgcolor "#CCCCCC"}
                                                 [:th "Project"] [:th "Lifecycle State"] [:th "Repositories"] [:th "Project Leads"]
                                               ]
                                             ]
                                             [:tbody
                                               (map render-project-row active-projects-with-unactioned-issues)
                                             ]
                                           ]
                                         ]
                                       ]
                                       [:p "To dig further into this data, please use the " [:a {:href "https://metrics.symphony.foundation/app/kibana#/dashboard/GitHub-Issues-Timing"} "metrics dashboard"] "."]
                                     ]
                                   ]
                         )]
          (send-email-to-esco (str "ESCo Report: Active Projects with Unactioned Issues, as at " now-str)
                                   [{ :type    "text/html; charset=\"UTF-8\""
                                      :content email-body }]))
          (println " ...📧 sent to" esco-email-address))
      (println "No active projects with unactioned issues - skipping report."))))

)