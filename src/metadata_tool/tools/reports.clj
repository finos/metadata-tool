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

(def ^:private inactive-project-days 180)   ; The age in days at which a project is considered "inactive"
(def ^:private old-pr-days           60)    ; The age in days at which a PR is considered "old"
(def ^:private old-issue-days        60)    ; The age in days at which an issue is considered "old"

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
;####TEST!!!!  Just to be 1000% sure we don't start spamming anyone.
(if-not (= "peter@symphony.foundation" to-address)
  (throw (Exception. (str "BAD EMAIL ADDRESS " to-address))))
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

(defn email-pmc-reports
  []
  (let [now-str                                 (tf/unparse (tf/formatter "yyyy-MM-dd h:mmaa ZZZ") (tm/now))
        all-programs                            (md/programs-metadata)
        inactive-unarchived-projects-metadata   (group-by :program-id
                                                          (remove #(= "ARCHIVED" (:state %))
                                                                  (remove nil?
                                                                          (map #(j/activity-with-team (md/activity-metadata-by-name %))
                                                                               (bi/inactive-projects inactive-project-days)))))
        unarchived-projects-with-unactioned-prs (group-by :program-id
                                                        (remove #(= "ARCHIVED" (:state %))
                                                                (remove nil?
                                                                        (map #(j/activity-with-team (md/activity-metadata-by-name %))
                                                                             (bi/projects-with-old-prs old-pr-days)))))]
    (log/info "Emailing" (count (keys inactive-unarchived-projects-metadata)) "PMC reports...")
    (doall (map #(send-email-to-pmc (:program-id %)
                                    (str "PMC Report for " (:program-short-name %) ", as at " now-str)
                                    (tem/render "emails/pmc-report.ftl"
                                                { :now                          now-str
                                                  :inactive-days                inactive-project-days
                                                  :old-pr-days                  old-pr-days
                                                  :program                      %
                                                  :inactive-projects            (get (:program-id %) inactive-unarchived-projects-metadata)
                                                  :projects-with-unactioned-prs (get (:program-id %) unarchived-projects-with-unactioned-prs)
                                                   } ))
                all-programs))
    (log/info "PMC reports sent.")))
