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
              [clojure.java.io                      :as io]
              [clojure.pprint                       :as pp]
              [hickory.select                       :as sel]
              [metadata-tool.sources.confluence     :as cfl]
              [metadata-tool.sources.metadata       :as md]
              ))

(def skip-pages ["template" "archive" "YYYY-MM-DD"])

(def years ["2019" "2018" "2017" "2016"])

(def ignore-names ["Individual's name" "Other Attendees" "FINOS Foundation"])

(def not-nil? (complement nil?))

(defn parse-date
    [title]
    (let [title-parsed (s/replace title "." "-")
          indexes (filter #(>= % 0) (remove nil? (map #(s/index-of title-parsed %) years)))]
        (if (not-empty indexes)
            (first (s/split
                (subs
                    title-parsed
                    (first indexes))
                #" "))
        title)))

(defn id-and-title
    [payload]
    {:id (:id payload) :title (:title payload)})

(defn skip-page
    [page-title]
    (> (count 
        (filter #(s/includes? 
            (s/upper-case page-title) 
            (s/upper-case %)) skip-pages)) 0))

(defn table-html
    [html]
    (let [first-table (str (first (s/split html #"</table>")) "</table>")
            after-h1-title (s/split first-table #"<h1>Attendees</h1>")
            after-h2-title (s/split first-table #"<h2>Attendees</h2>")]
        (let [payload
                (if 
                    (> (count after-h1-title) 1)
                    (second after-h1-title)
                    (if 
                        (> (count after-h2-title) 1)
                        (second after-h2-title)))]
            (if (and
                    payload
                    (s/starts-with? (s/trim payload) "<table"))
                (s/trim payload)))))

(defn resolve-user
    [element]
    (let [user-element (sel/select (sel/descendant (sel/tag (keyword "ri:user"))) element)
          select-leaf (sel/not (sel/has-child sel/any))]
        (if (not-empty user-element)
            (let [user-key (get (:attrs (first user-element)) (keyword "ri:userkey"))
                  body (:body (cfl/cget (str "user?expand=email&key=" user-key)))]
                [(:email body) (:displayName body)])
            (if-let [name (:content (first (sel/select select-leaf element)))]
                [nil (s/trim (apply str name))]
                nil))))

(defn row-to-user
    [row program activity meeting-date]
    (let [items      (sel/select (sel/child (sel/tag :tr) (sel/tag :td)) row)
          id         (resolve-user (first items))
          name       (second id)
          orgItem    (second items)
          select-leaf   (sel/not (sel/has-child sel/any))
          org        (or 
                        (apply str (:content (first (sel/select sel/last-child orgItem))))
                        (apply str (:content (first (sel/select select-leaf orgItem)))))
          ghid          (if (> (count items) 2) (:content (first (sel/select select-leaf (nth items 2)))) nil)
          user-by-gh    (md/person-metadata-by-github-login-fn ghid)
          user-by-name  (md/person-metadata-by-fullname-fn name)
          user-by-email (md/person-metadata-by-email-address-fn (first id))]
        (if-not (or
            (some #(= name %) ignore-names)
            (and 
                (s/blank? name)
                (s/blank? org)
                (s/blank? ghid))) {
                :email (or 
                    (first (:email-addresses user-by-name))
                    (first (:email-addresses user-by-gh))
                    (first id))
                :name (or
                    (:full-name user-by-email)
                    (:full-name user-by-gh)
                    name)
                :org (or
                    (:organization-name (first (md/current-affiliations (:person-id user-by-email))))
                    (:organization-name (first (md/current-affiliations (:person-id user-by-name))))
                    (:organization-name (first (md/current-affiliations (:person-id user-by-gh))))
                    org)
                :ghid (or
                    (first (:github-logins user-by-email))
                    (first (:github-logins user-by-name))
                    (first (:github-logins user-by-gh)))
                    ; TODO - cannot rely on GitHub data as third column;
                    ; right now it contains all sorts of data
                    ; ghid)
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

(defn ids-and-titles
    [id]
    (let [children (map #(id-and-title %) (cfl/children id))]
        (flatten
            (concat 
                children
                (map #(ids-and-titles (:id %)) children)))))

(defn meetings-rosters
    [program activity url]
    (let [page-id   (cfl/page-id url)
          sub-pages (ids-and-titles page-id)]
        (remove nil? (flatten (map 
            #(parse-page % program activity)
            sub-pages)))))

(defn roster-to-csv
    [roster-data]
    (with-open [writer (io/writer "roster-data.csv")]
        (.write writer "email, name, org, github ID, program, activity, date\n")
        (doall 
        (map 
            #(.write writer (str (s/join ", " (vals %)) "\n")) 
            roster-data))
        (.flush writer)))
              