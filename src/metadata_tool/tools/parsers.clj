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
              [metadata-tool.sources.confluence     :as cfl]
              [metadata-tool.sources.metadata       :as md]
              ))

; Meeting minute
(def minute "https://finosfoundation.atlassian.net/wiki/spaces/DT/pages/486080560/kdb+WG+Minutes+-+2018.09.05")

; (metadata-tool.tools.parsers/users (metadata-tool.sources.confluence/meeting-roster (metadata-tool.sources.confluence/page-id metadata-tool.tools.parsers/minute)))

(def url "https://finosfoundation.atlassian.net/wiki/spaces/DT/pages/329383945/kdb+Working+Group")

(def skip-pages ["template" "archive"])

(defn parse-date
    [title]
    title)

(defn id-and-title
    [payload]
    {:id (:id payload) :title (:title payload)})

(defn skip-page
    [page-title]
    (> (count 
        (filter #(s/includes? 
            (s/upper-case page-title) 
            (s/upper-case %)) skip-pages)) 0))

; (metadata-tool.tools.parsers/table-html (metadata-tool.sources.confluence/meeting-roster (metadata-tool.sources.confluence/page-id metadata-tool.tools.parsers/minute)))
; (metadata-tool.tools.parsers/table-html (metadata-tool.sources.confluence/meeting-roster (metadata-tool.sources.confluence/page-id metadata-tool.sources.confluence/url)))
(defn table-html
    [html]
    (let [first-table (str (first (s/split html #"</table>")) "</table>")
            after-h1-title (s/split first-table #"<h1>Attendees</h1>")
            after-h2-title (s/split first-table #"<h2>Attendees</h2>")]
        (if 
            (> (count after-h1-title) 1)
            (second after-h1-title)
            (if 
                (> (count after-h2-title) 1)
                (second after-h2-title)))))

; (metadata-tool.tools.parsers/resolve-user t1)
(defn resolve-user
    [element]
    (let [user-element (sel/select (sel/descendant (sel/tag (keyword "ri:user"))) element)]
        (if (not-empty user-element)
            (let [user-key (get (:attrs (first user-element)) (keyword "ri:userkey"))
                  body (:body (cfl/cget (str "user?expand=email&key=" user-key)))]
                [(:email body) (:displayName body)])
            (if-let [name (:content element)]
                [nil name]
                nil))))

; (metadata-tool.tools.parsers/row-to-user t1)
(defn row-to-user
    [row program activity meeting-date]
    (let [items      (sel/select (sel/child (sel/tag :tr) (sel/tag :td)) row)
          id         (resolve-user (first items))
          orgItem    (second items)
          org        (or 
                        (:content (first (sel/select sel/last-child orgItem)))
                        (:content orgItem))
          ghid        (if (> (count items) 2) (:content (nth items 2)) nil)
          user-by-gh    (md/person-metadata-by-github-login-fn ghid)
          user-by-email (md/person-metadata-by-email-address-fn (first id))]
        (if-not (and (nil? id) (nil? org) (nil? ghid))    
            {
                :email (or 
                    (first (:email-addresses user-by-email))
                    (first (:email-addresses user-by-gh))
                    (first id))
                :name (or
                    (:full-name user-by-email)
                    (:full-name user-by-gh)
                    (apply str (second id)))
                :org (or
                    (:organization-name (first (md/current-affiliations (:person-id user-by-email))))
                    (:organization-name (first (md/current-affiliations (:person-id user-by-gh))))
                    (apply str org))
                :ghid ghid
                :program program
                :activity activity
                :meeting-date meeting-date}
            nil)))

(defn meeting-roster
    [table-html page-title program activity]
    (let [meeting-date (parse-date page-title)
          selector    (sel/tag :tr)]
        (let [table (html/as-hickory (html/parse table-html))
              rows  (sel/select selector table)]
            (map 
                #(row-to-user % program activity meeting-date)
                rows))))

(defn parse-page
    [page-data program activity]
    (let [content (cfl/content (:id page-data))
          title (:title page-data)]
        (if-not (skip-page title)
            (let [table-html (table-html (cfl/content (:id page-data)))]
                (if-not (empty? table-html)
                    (meeting-roster 
                        table-html
                        title
                        program
                        activity)))
            [])))

; (metadata-tool.tools.parsers/ids-and-titles (metadata-tool.sources.confluence/page-id metadata-tool.sources.confluence/url))
(defn ids-and-titles
    [id]
    (let [children (map #(id-and-title %) (cfl/children id))]
        (flatten
            (concat 
                children
                (map #(ids-and-titles (:id %)) children)))))

; (metadata-tool.tools.parsers/meetings-rosters metadata-tool.tools.parsers/url)
(defn meetings-rosters
    [program activity url]
    (let [page-id   (cfl/page-id url)
          sub-pages (ids-and-titles page-id)]
        (remove nil? (flatten (map 
            #(parse-page % program activity)
            sub-pages)))))
