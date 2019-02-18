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
(ns metadata-tool.tools.parsers
    (:require [clojure.string                       :as s]
              [hickory.core                         :as html]
              [hickory.select                       :as sel]
              [metadata-tool.sources.confluence      :as cfl]
              ))

; Meeting minute
(def minute "https://finosfoundation.atlassian.net/wiki/spaces/DT/pages/486080560/kdb+WG+Minutes+-+2018.09.05")

; (metadata-tool.tools.parsers/tableHtml (metadata-tool.sources.confluence/meetingRoster (metadata-tool.sources.confluence/pageId metadata-tool.tools.parsers/minute)))
; (metadata-tool.tools.parsers/tableHtml (metadata-tool.sources.confluence/meetingRoster (metadata-tool.sources.confluence/pageId metadata-tool.sources.confluence/url)))
(defn tableHtml
    [html]
    (let [firstTable (str (first (s/split html #"</table>")) "</table>")
            afterH1Title (s/split firstTable #"<h1>Attendees</h1>")
            afterH2Title (s/split firstTable #"<h2>Attendees</h2>")]
        (if 
            (> (count afterH1Title) 1)
            (second afterH1Title)
            (if 
                (> (count afterH2Title) 1)
                (second afterH2Title)))))

; (metadata-tool.tools.parsers/resolveUser t1)
(defn resolveUser
    [element]
    (let [userElement (sel/select (sel/descendant (sel/tag (keyword "ri:user"))) element)]
        (if (not-empty userElement)
            (let [userKey (get (:attrs (first userElement)) (keyword "ri:userkey"))
                  body (:body (cfl/cget (str "user?expand=email&key=" userKey)))]
                [(:email body) (:displayName body)])
            (:content element))))

; (metadata-tool.tools.parsers/rowToUser t1)
(defn rowToUser
    [row program activity meetingDate]
    (let [items   (sel/select (sel/child (sel/tag :tr) (sel/tag :td)) row)
          id      (resolveUser (first items))
          orgItem (second items)
          org     (or 
                        (:content (first (sel/select sel/last-child orgItem)))
                        (:content orgItem))
          ghid    (if (> (count items) 2) (:content (nth items 2)) nil)]
        (flatten
            [id org ghid program activity meetingDate])))

; (metadata-tool.tools.parsers/users (metadata-tool.sources.confluence/meetingRoster (metadata-tool.sources.confluence/pageId metadata-tool.tools.parsers/minute)))

(def url "https://finosfoundation.atlassian.net/wiki/spaces/DT/pages/329383945/kdb+Working+Group")

(defn parseDate
    [title]
    title)

(defn users
    [html pageTitle program activity]
    (let [tableHtml (tableHtml html)
          meetingDate (parseDate pageTitle)]
        (if (empty? tableHtml)
            []
            (let [table     (html/as-hickory (html/parse tableHtml))
                  selector  (sel/tag :tr)
                  rows      (sel/select selector table)]
                    (map #(rowToUser % program activity meetingDate) rows)))))

(defn parsePage
    [pageData program activity]
    (users 
        (cfl/meetingRoster (:id pageData)) (:title pageData) program activity))

; (metadata-tool.tools.parsers/meetingsRosters metadata-tool.tools.parsers/url)
(defn meetingsRosters
    [program activity url]
    (let [pageId (cfl/pageId url)]
        (map 
            #(parsePage % program activity)
            (cfl/meetingsIds pageId))))
