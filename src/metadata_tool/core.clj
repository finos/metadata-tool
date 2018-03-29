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
(ns metadata-tool.core
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
            [metadata-tool.tools.checkers   :as tch]
            [metadata-tool.tools.listers    :as tls]
;            [metadata-tool.tools.generators :as tgn]
;            [metadata-tool.tools.reports    :as trp]
            ))



; Map of (lowercase) tool names to tool fns
(def ^:private tools
  {
    "check-local"                                         tch/check-local
    "check"                                               tch/check
    "list-schemas"                                        tls/list-schemas
    "list-clas"                                           tls/list-people-with-clas
;    "check-bitergia-projects"                             tch/check-bitergia-projects
;    "list-projects"                                       tls/list-projects
;    "list-project-leads"                                  tls/list-project-leads
;    "list-projects-repos-and-team"                        tls/list-projects-repos-and-team
;    "list-project-contrib-history"                        tls/list-project-contrib-history-csv
;    "list-org-users"                                      tls/list-org-users
;    "list-active-projects"                                tls/list-active-projects
;    "list-inactive-projects"                              tls/list-inactive-projects
;    "list-projects-with-old-prs"                          tls/list-projects-with-old-prs
;    "list-active-projects-with-old-prs"                   tls/list-active-projects-with-old-prs
;    "gen-bitergia-affiliation-data"                       tgn/gen-bitergia-affiliation-data
;    "gen-bitergia-project-file"                           tgn/gen-bitergia-project-file
;    "gen-project-metadata"                                tgn/gen-project-metadata
;    "gen-project-meta-for-website"                        tgn/gen-project-meta-for-website
;    "gen-clabot-whitelist"                                tgn/gen-clabot-whitelist
;    "email-inactive-projects-report"                      trp/email-inactive-projects-report
;    "email-active-projects-with-unactioned-prs-report"    trp/email-active-projects-with-unactioned-prs-report
;    "email-active-projects-with-unactioned-issues-report" trp/email-active-projects-with-unactioned-issues-report
  })

(def tool-names (sort (keys tools)))

(defn run-tool
  "Runs the given tool."
  [tool]
  (if-let [tool-fn (get tools (s/lower-case tool))]
    (do
      (tool-fn)
      nil)))   ; Force nil return value
