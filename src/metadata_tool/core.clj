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
(ns metadata-tool.core
  (:require [clojure.string                 :as str]
            [metadata-tool.tools.checkers   :as tch]
            [metadata-tool.tools.listers    :as tls]
            [metadata-tool.tools.generators :as tgn]
            [metadata-tool.tools.reports    :as trp]))

(def ^:private tools
  "Map of (lowercase) tool names to tool fns"
  {"check-local"                    tch/check-local
   "check"                          tch/check
   "list-schemas"                   tls/list-schemas
   "list-people-with-clas"          tls/list-people-with-clas
   "gen-meeting-roster-data"        tgn/gen-meeting-roster-data
   "gen-bitergia-affiliation-data"  tgn/gen-bitergia-affiliation-data
   "gen-bitergia-organization-data" tgn/gen-bitergia-organization-data
   "gen-bitergia-project-data"      tgn/gen-bitergia-project-data
   "gen-clabot-whitelist"           tgn/gen-clabot-whitelist
   "gen-clabot-ids-whitelist"       tgn/gen-clabot-ids-whitelist
   "gen-catalogue-data"             tgn/gen-catalogue-data
   "email-pmc-reports"              trp/email-pmc-reports})

(def tool-names (sort (keys tools)))

(defn run-tool
  "Runs the given tool."
  [tool]
  (if-let [tool-fn (get tools (str/lower-case tool))]
    (do
      (tool-fn)
      nil)))   ; Force nil return value
