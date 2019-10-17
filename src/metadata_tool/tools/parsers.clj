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
  (:require [clojure.string                       :as str]
            [hickory.core                         :as html]
            [clojure.java.io                      :as io]
            [hickory.select                       :as sel]
            [metadata-tool.sources.confluence     :as cfl]
            [metadata-tool.config                 :as cfg]
            [clojure.pprint                       :as pp]
            [metadata-tool.sources.metadata       :as md]))

(def not-nil? (complement nil?))

(defn parse-string
  [s]
  (if (empty? s)
    ""
    (-> s
        (str/replace "&nbsp;" " ")
        (str/replace "\u00A0" " ")
        str/trim)))

(defn resolve-acronym
  [string]
  (if-let [acronym (get (:acronyms (:meetings cfg/config)) string)]
    acronym
    string))

(defn parse-name
  [string to-remove]
  (when-not (str/blank? string)
    (if (empty? to-remove)
      string
      (parse-name
      (str/replace string (first to-remove) "")
      (rest to-remove)))))

(defn parse-date
  [title]
  (let [title-parsed (str/replace title "." "-")
        indexes
        (filter #(>= % 0)
                (keep #(str/index-of title-parsed %)
                      (:years (:meetings cfg/config))))]
    (if (not-empty indexes)
      (first (str/split
              (subs
               title-parsed
               (first indexes))
              #" "))
      title)))

(defn id-and-title
  [payload]
  {:id (:id payload) :title (:title payload) :url (:webui (:_links payload))})

(defn skip-page
  [page-title]
  (pos?
   (count
    (filter #(str/includes?
              (str/upper-case page-title)
              (str/upper-case %)) (:skip-pages (:meetings cfg/config))))))

(defn extract-full-name
  [element]
  (let [ancor (sel/select (sel/tag :a) element)]
    (if (empty? ancor)
      (let [span (sel/select (sel/descendant (sel/tag :span)) element)]
        (if (empty? span)
          (let [any (sel/select (sel/descendant sel/any sel/any) element)]
            (if (empty? any)
              (first (:content element))
              (first (:content any))))
        (first (:content (first span)))))
      (first (:content (first ancor))))))

(defn debug
  [x]
  (println "--- START DEBUG ---")
  (pp/pprint x)
  (println "--- END DEBUG ---")
  x)

(defn row-to-user
  [row program activity type meeting-date]
  (let [raw-name (extract-full-name row)
        ignored-names (:ignore-names (:meetings cfg/config))]
    (if-let [name (resolve-acronym (parse-name (parse-string raw-name) (:remove-from-names (:meetings cfg/config))))]
      (if-not (contains? ignored-names name)
        (if-let [user-by-md (md/person-metadata-by-fn nil (str/trim name) nil)] {
          :email (first (:email-addresses user-by-md))
          :name (:full-name user-by-md)
          :org (or (:organization-name (first (md/current-affiliations (:person-id user-by-md)))))
          :ghid (or (first (:github-logins user-by-md)))
          :program program
          :activity activity
          :type type
          :meeting-date meeting-date }
          (println "[USER NOT FOUND] " (str "'" (str/trim name) "'") "on activity" activity "date" meeting-date))))))

(defn meeting-roster
  [raw-table-html page-title program activity type]
  (let [meeting-date (parse-date page-title)
        table-html   (str "<table>" raw-table-html "</table>")
        selector     (sel/and (sel/class :confluenceTd) sel/first-child)]
    (let [table (html/as-hickory (html/parse table-html))
          rows  (sel/select selector table)]
      (map
       #(row-to-user % program activity type meeting-date)
       rows))))

(defn parse-page
  [page-data program activity type]
  (let [title (:title page-data)
        meeting-date (parse-date title)]
    (if (not (= meeting-date title))
      (let [content (cfl/content (:url page-data))]
        (if-not (skip-page title) (do
          (println (str "Scanning for meeting attendance on page " (:title page-data)))
          (if-not (empty? content)
            (meeting-roster
              content
              title
              program
              activity
              type))))))))

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
    (flatten (keep
              #(parse-page % program activity type)
              sub-pages))))

(defn roster-to-csv
  [roster-data]
  (with-open [writer (io/writer "finos-meetings.csv")]
    (.write writer "email, name, org, github ID, cm_program, cm_title, cm_type, date\n")
    (doall
     (map
      #(.write writer (str (str/join ", " (vals %)) "\n"))
      ; DEBUG - Check on :name instead of :email if you want to debug output
      (remove #(or (nil? %) (nil? (:email %))) roster-data)))
    (.flush writer)))
