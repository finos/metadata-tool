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
            [clojure.pprint                       :as pp]
            [clojure.java.io                      :as io]
            [clojure.set                          :as set]
            [clojure.data.json                    :as json]
            [clojure.data.csv                     :as csv]
            [hickory.core                         :as html]
            [hickory.select                       :as sel]
            [metadata-tool.sources.confluence     :as cfl]
            [metadata-tool.config                 :as cfg]
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

(defn resolve-acronym [s] (get (:acronyms (:meetings cfg/config)) s s))

(defn parse-name
  [s to-remove]
  (when-not (str/blank? s)
    (if (empty? to-remove)
      s
      (parse-name
      (str/replace s (first to-remove) "")
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
  {:id (:id payload)
   :title (:title payload)
   :url (:webui (:_links payload))})

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
          (let [p (sel/select (sel/descendant (sel/tag :p)) element)]
            (if (empty? p)
              (let [any (sel/select (sel/descendant sel/any sel/any) element)]
                (if (empty? any)
                  (first (:content element))
                  (first (:content any))))
              (first (:content (first p)))))
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
      (if-not (contains? ignored-names (str/trim name))
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

;; Utility functions for gen-meeting-github-roster-data
;; 
(defn string-keys-to-symbols [map]
  (reduce #(assoc %1 (-> (key %2) keyword) (val %2)) {} map))

(defn csv-item-to-str
  [item]
  (if-not (empty? item)
    (str (str/join ", " (map #(str/trim %) item)))))

(defn str-to-csv-item
  [s]
  (map #(str/trim %) (str/split s #",")))

(defn contains-not
  [list item]
  (not (some #(= item %) list)))

(defn get-json
  [path]
  (json/read-str (slurp path)))

(defn to-json
  [o]
  (json/write-str o))

(defn get-reader
  [path]
  (io/reader path))

(defn get-writer
  [path]
  (io/writer path))

(defn read-csv
  [reader]
  (csv/read-csv reader))

(defn write-csv
  [writer items]
  (csv/write-csv writer items))

(defn has-meeting-config
  []
  (and
   (.exists (io/as-file "./meeting-attendance.json"))
   (.exists (io/as-file "./github-finos-meetings.csv"))))

(defn match-project
  [project data]
  (and
   (some #(= (str/lower-case (:repo data)) (str/lower-case %))
         (:github-repos project))
   (some #(str/includes? % (str "/" (:org data) "/"))
         (:github-urls project))))

(defn get-csv-delta
  [new-csv]
  (with-open
   [reader (io/reader "./github-finos-meetings.csv")]
    (let [items     (set (map #(csv-item-to-str %) (csv/read-csv reader)))
          new-items (set (map #(csv-item-to-str (vals %)) new-csv))
          exist-str (set/intersection items new-items)
          existing  (map #(str-to-csv-item %) exist-str)
          missing   (map #(str-to-csv-item %) (set/difference new-items exist-str))]
      [existing missing])))

(defn single-attendance
  [person project meeting-date]
  (if-let [email
           (cond
             (> (count (:email-addresses person)) 0) (first (:email-addresses person))
             (> (count (:github-logins person)) 0) (str (first (:github-logins person))
                                                        "@users.noreply.github.com"))]
    {:email email
     :name (:full-name person)
     :org (or (:organization-name (first (md/current-affiliations (:person-id person)))))
     :ghid (or (first (:github-logins person)))
     :program (:program-short-name project)
     :activity (:activity-name project)
     :type (:type project)
     :meeting-date meeting-date}))

(defn remove-existing-entries
  [to-remove current]
  (let [remove-str (set (map #(csv-item-to-str %) to-remove))
        curr-str   (map #(csv-item-to-str %) current)
        diff-str   (filter #(contains-not remove-str %) curr-str)]
    (map #(str-to-csv-item %) diff-str)))
