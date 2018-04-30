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
(ns metadata-tool.sources.joins
  (:require [clojure.string                 :as s]
            [clojure.tools.logging          :as log]
            [clojure.set                    :as set]
            [mount.core                     :as mnt :refer [defstate]]
            [postal.core                    :as email]
            [metadata-tool.config           :as cfg]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.metadata :as md]
            [metadata-tool.sources.bitergia :as bt]))


(defn activity-with-team
  "The given activity, augmented with the project team, as :project-leads and :committers."
  [activity]
  (if (and activity
           (seq (:github-urls activity)))
    (assoc activity
           :admins      (seq
                          (sort-by :full-name
                            (map md/person-metadata-by-github-login
                                 (distinct
                                   (remove nil?
                                     (mapcat gh/admin-logins (:github-urls activity)))))))
            :committers (seq
                          (sort-by :full-name
                            (map md/person-metadata-by-github-login
                                 (distinct
                                   (remove nil?
                                     (mapcat gh/committer-logins (:github-urls activity))))))))))
