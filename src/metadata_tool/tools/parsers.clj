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
            [metadata-tool.config                 :as cfg]
            [metadata-tool.sources.metadata       :as md]))

(def not-nil? (complement nil?))

(defn parse-string
  [string]
  (if (s/blank? string)
    string
    (let [no-spaces-str  (s/replace string "&nbsp;" " ")
          no-unicode-str (s/replace no-spaces-str "\u00A0" " ")]
      (s/trim no-unicode-str))))

(defn parse-name
  [string to-remove]
  (when-not (s/blank? string)
    (if (empty? to-remove)
      string
      (if-let [acronym (get (:acronyms (:confluence cfg/config)) string)]
        acronym
        (parse-name
         (s/replace string (first to-remove) "")
         (rest to-remove))))))

(defn parse-date
  [title]
  (let [title-parsed (s/replace title "." "-")
        indexes
        (filter #(>= % 0)
                (remove nil? (map #(s/index-of title-parsed %)
                                  (:years (:confluence cfg/config)))))]
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
  (pos?
   (count
    (filter #(s/includes?
              (s/upper-case page-title)
              (s/upper-case %)) (:skip-pages (:confluence cfg/config))))))

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
      (when-let [name (:content (first (sel/select select-leaf element)))]
        [nil (parse-string (apply str name))]))))

(defn row-to-user
  [row program activity type meeting-date]
  (let [items      (sel/select (sel/child (sel/tag :tr) (sel/tag :td)) row)
        id         (resolve-user (first items))
        email      (first id)
        name       (parse-string (parse-name (second id) (:remove-from-names (:confluence cfg/config))))
        orgItem    (second items)
        select-leaf   (sel/not (sel/has-child sel/any))
        org        (or
                    (apply str (:content (first (sel/select sel/last-child orgItem))))
                    (apply str (:content (first (sel/select select-leaf orgItem)))))
        ghid          (when (> (count items) 2)
                        (:content (first (sel/select select-leaf (nth items 2)))))
        user-by-md    (md/person-metadata-by-fn nil name email)]
        ; (if-not (s/blank? name)
        ;     (let []
        ;         (println (str 
        ;             "'" name "' '" org "' '" ghid 
        ;             "(" (Character/codePointAt name (dec (count name))) ")"))))
    (if-not (or
             (some #(= name %) (:ignore-names (:confluence cfg/config)))
             (and
              (s/blank? name)
              (s/blank? org)
              (s/blank? ghid))) {:email (or
                                         (first (:email-addresses user-by-md))
                                         (first id))
                                 :name (or
                                        (:full-name user-by-md)
                                        name)
                                 :org (or
                                       (:organization-name (first (md/current-affiliations (:person-id user-by-md))))
                                       org)
                                 :ghid (or
                                        (first (:github-logins user-by-md)))
                    ; TODO - cannot rely on GitHub data as third column;
                    ; right now it contains all sorts of data
                    ; ghid)
                                 :program program
                                 :activity activity
                                 :type type
                                 :meeting-date meeting-date}
            nil)))

(defn meeting-roster
  [table-html page-title program activity type]
  (let [meeting-date (parse-date page-title)
        selector    (sel/tag :tr)]
    (let [table (html/as-hickory (html/parse table-html))
          rows  (sel/select selector table)]
      (map
       #(row-to-user % program activity type meeting-date)
       rows))))

(defn parse-page
  [page-data program activity type]
  (let [content (cfl/content (:id page-data))
        title (:title page-data)]
    (if-not (skip-page title)
      (let [table-html (table-html (cfl/content (:id page-data)))]
        (if-not (empty? table-html)
          (meeting-roster
           table-html
           title
           program
           activity
           type)))
      [])))

(defn ids-and-titles
  [id]
  (let [children (map id-and-title (cfl/children id))]
    (flatten
     (concat
      children
      (map #(ids-and-titles (:id %)) children)))))

(defn meetings-rosters
  [program activity type url]
  (println (str "Generating meeting attendance for activity " activity))
  (let [page-id   (cfl/page-id url)
        sub-pages (ids-and-titles page-id)]
    (remove nil? (flatten (map
                           #(parse-page % program activity type)
                           sub-pages)))))

(defn roster-to-csv
  [roster-data]
  (with-open [writer (io/writer "finos-meetings.csv")]
    (.write writer "email, name, org, github ID, cm_program, cm_title, cm_type, date\n")
    (doall
     (map
      #(.write writer (str (s/join ", " (vals %)) "\n"))
      roster-data))
    (.flush writer)))
