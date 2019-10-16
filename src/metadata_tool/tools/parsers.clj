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

(defn remove-from-name
  [string to-remove]
  (when-not (str/blank? string)
    (if (empty? to-remove)
      string
      (if-let [acronym (get (:acronyms (:confluence cfg/config)) string)]
        acronym
        (remove-from-name
         (str/replace string (first to-remove) "")
         (rest to-remove))))))

(defn parse-date
  [title]
  (let [title-parsed (str/replace title "." "-")
        indexes
        (filter #(>= % 0)
                (keep #(str/index-of title-parsed %)
                      (:years (:confluence cfg/config))))]
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
              (str/upper-case %)) (:skip-pages (:confluence cfg/config))))))

(defn row-to-user
  [row program activity type meeting-date]
  (let [[raw-name] (:content row)
        name (remove-from-name raw-name (:remove-from-names (:confluence cfg/config)))
        ignored-names (:ignore-names (:confluence cfg/config))
        user-by-md (md/person-metadata-by-fn nil name nil)]
    (if-not (contains? (:ignore-names (:confluence cfg/config)) name) {
      :email (first (:email-addresses user-by-md))
      :name (:full-name user-by-md)
      :org (or (:organization-name (first (md/current-affiliations (:person-id user-by-md)))))
      :ghid (or (first (:github-logins user-by-md)))
      :program program
      :activity activity
      :type type
      :meeting-date meeting-date })))

(defn meeting-roster
  [table-html page-title program activity type]
  (let [meeting-date (parse-date page-title)
        selector    (sel/tag :a)]
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
        (if-not (skip-page title)
          (if-not (empty? content)
            (meeting-roster
              content
              title
              program
              activity
              type)))))))

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
      (remove #(or (nil? %) (nil? (:email %))) roster-data)))
    (.flush writer)))
