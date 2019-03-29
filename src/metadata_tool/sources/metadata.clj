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
(ns metadata-tool.sources.metadata
  (:require [clojure.string                :as s]
            [clojure.tools.logging         :as log]
            [clojure.java.io               :as io]
            [mount.core                    :as mnt :refer [defstate]]
            [cheshire.core                 :as ch]
            [clj-time.core                 :as tm]
            [clj-time.format               :as tf]
            [metadata-tool.config          :as cfg]
            [metadata-tool.sources.github  :as gh]
            [metadata-tool.sources.schemas :as sch]))

(defstate ^:private organization-metadata-directory :start (str gh/metadata-directory "/organizations"))
(defstate ^:private people-metadata-directory       :start (str gh/metadata-directory "/people"))
(defstate ^:private program-metadata-directory      :start (str gh/metadata-directory "/programs"))

(def ^:private organization-filename "organization-metadata.json")
(def ^:private person-filename       "person-metadata.json")
(def ^:private program-filename      "program-metadata.json")
(def ^:private activity-filename     "activity-metadata.json")
(def ^:private repository-filename   "repository-metadata.json")

(defn- list-metadata-files
  [filename]
  (doall
    (sort-by #(.getCanonicalPath ^java.io.File %)
             (filter #(= filename (.getName ^java.io.File %)) (file-seq (io/file gh/metadata-directory))))))

(defstate ^:private organization-metadata-files :start (list-metadata-files organization-filename))
(defstate ^:private person-metadata-files       :start (list-metadata-files person-filename))
(defstate ^:private program-metadata-files      :start (list-metadata-files program-filename))
(defstate ^:private activity-metadata-files     :start (list-metadata-files activity-filename))
(defstate ^:private repository-metadata-files   :start (list-metadata-files repository-filename))

(defstate ^:private metadata-files
  :start { :organization organization-metadata-files
           :person       person-metadata-files
           :program      program-metadata-files
           :activity     activity-metadata-files
           :repository   repository-metadata-files })

(defn- list-subdirs
  "Returns a sequence of the immediate subdirectories of dir, as java.io.File objects."
  [^java.io.File dir]
  (seq (.listFiles dir
                   (reify
                     java.io.FileFilter
                     (accept [this f]
                       (.isDirectory f))))))

(defstate organizations :start (doall (sort (map #(.getName ^java.io.File %) (list-subdirs (io/file organization-metadata-directory))))))
(defstate people        :start (doall (sort (map #(.getName ^java.io.File %) (list-subdirs (io/file people-metadata-directory))))))
(defstate programs      :start (doall (sort (map #(.getName ^java.io.File %) (list-subdirs (io/file program-metadata-directory))))))

(defn- clojurise-json-key
  "Converts nasty JSON String keys (e.g. \"fullName\") to nice Clojure keys (e.g. :full-name)."
  [k]
  (keyword
    (s/replace
      (s/join "-"
              (map s/lower-case
                   (s/split k #"(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")))
      "git-hub"
      "github")))

(defn- read-metadata-file-fn
  [metadata-file]
  (let [the-file (io/file metadata-file)]
    (when (.exists the-file)
      (ch/parse-string (slurp the-file) clojurise-json-key))))
(def ^:private read-metadata-file (memoize read-metadata-file-fn))

(defn- validate-metadata-file
  "Validates the given metadata file against the given schema-type, automatically determining which version the metadata file is."
  [schema-type ^java.io.File file]
  (log/debug "Validating" schema-type "metadata file" (.getCanonicalPath file))
  (try
    (let [json-string      (slurp file)
          json             (ch/parse-string json-string clojurise-json-key)
          metadata-version (:metadata-version json)
          schema-id        [schema-type metadata-version]]
      (if metadata-version
        (sch/validate-json schema-id json-string)
        (throw (Exception. (str "No metadataVersion property.")))))
    (catch Exception e
      (throw (Exception. (str (.getCanonicalPath file) " failed to validate, due to " (.getMessage e)) e)))))

(defn- validate-metadata-files
  [schema-type files]
  (doall (map (partial validate-metadata-file schema-type) files)))

(defn validate-metadata
  "Validates all metadata in the repository."
  []
  (doall (map #(validate-metadata-files (key %) (val %)) metadata-files)))

(defn organization-metadata
  "Organization metadata of the given organization-id, or nil if there is none."
  [organization-id]
  (if organization-id
    (if-let [result (read-metadata-file (str organization-metadata-directory "/" organization-id "/" organization-filename))]
      (assoc result
             :organization-id organization-id))))

(defn organizations-metadata
  "A seq containing the metadata of all organizations, sorted by organization-name."
  []
  (sort-by :organization-name (remove nil? (map organization-metadata organizations))))

(defn person-metadata
  "Person metadata of the given person-id, or nil if there is none."
  [person-id]
  (if person-id
    (if-let [person-metadata (read-metadata-file (str people-metadata-directory "/" person-id "/" person-filename))]
      (assoc person-metadata
             :person-id person-id))))

(defn person-metadata-with-organizations
  "Person metadata of the given person-id, with affiliations expanded to include full organization metadata."
  [person-id]
  (if-let [person (person-metadata person-id)]
    (if-let [affiliations (:affiliations person)]
      (assoc person
             :affiliations (seq (map #(assoc % :organization (organization-metadata (:organization-id %))) affiliations)))
      person)))

(defn people-metadata
  "A seq containing the metadata of all people, sorted by full-name."
  []
  (sort-by :full-name (remove nil? (map person-metadata people))))

(defn people-metadata-with-organizations
  "A seq containing the metadata of all people, sorted by full-name."
  []
  (sort-by :full-name (remove nil? (map person-metadata-with-organizations people))))

(defn person-metadata-by-github-login-fn
  [github-login]
  (if github-login
    (first (filter #(some #{github-login} (:github-logins %)) (people-metadata)))))
(def person-metadata-by-github-login
  "Person metadata of the given GitHub login, or nil if there is none."
  (memoize person-metadata-by-github-login-fn))

(defn matches-person
  [person ghid name email]
  (or
      (and
        ; (try
        (not (s/blank? ghid))
          ; (catch Exception e (str ghid " " name " " email " - caught exception: " (.getMessage e))))
        (some #{ghid} (:github-logins person)))
      (and
        (not (s/blank? name))
        (= name (:full-name person)))
      (and
        (not (s/blank? email))
        (some #{email} (:email-addresses person)))))
  
(defn person-metadata-by-fn
  [ghid name email]
  (if (or ghid name email)
    (first (filter #(matches-person % ghid name email) (people-metadata)))))
(def person-metadata-by
  "Person metadata of either a given GitHub login, name or email address; returns nil if there is none."
  (memoize person-metadata-by-fn))
  
(defn lower-emails
  [item]
  (map #(s/lower-case %) (:email-addresses item)))

(defn person-metadata-by-email-address-fn
    [email-address]
    (if email-address
      (first (filter #(some 
                        #{(s/lower-case email-address)}
                        (lower-emails %)) (people-metadata)))))
(def person-metadata-by-email-address
  "Person metadata of the given email address, or nil if there is none."
  (memoize person-metadata-by-email-address-fn))
  
(defn person-metadata-by-fullname-fn
  [full-name]
  (if full-name
    (first (filter #(= full-name (:full-name %)) (people-metadata)))))
(def person-metadata-by-fullname
  "Person metadata of the given fullname, or nil if there is none."
  (memoize person-metadata-by-email-address-fn))

(defn- program-activities
  "A seq of the ids of all activities in the given program."
  [program-id]
  (sort (map #(.getName ^java.io.File %) (list-subdirs (io/file (str program-metadata-directory "/" program-id))))))

(defn- github-urls
  [program repos]
  (seq (map #(str "https://github.com/" (:github-org program) "/" %) repos)))

(defn- program-activity-github-urls
  [program activity]
  (github-urls program (:github-repos activity)))

(defn- pmc-github-urls
  [program]
  (github-urls program (:pmc-repos program)))
  ; (github-urls program (map #(s/lower-case %) (:pmc-repos program))))
  
(defn- expand-mailing-list-address
  [mailing-list-address]
  (if-not (s/blank? mailing-list-address)
    {
      :email-address   mailing-list-address
      :web-archive-url (let [[list-name domain] (s/split mailing-list-address #"@")]
                         (if (and (not (s/blank? list-name))
                                  (not (s/blank? domain))
                                  (or (= domain "finos.org")
                                      (= domain "symphony.foundation")))
                           (str "https://groups.google.com/a/" domain "/forum/#!forum/" list-name)))
    }))

(defn- expand-confluence-space-key
  [confluence-space-key]
  (if-not (s/blank? confluence-space-key)
    {
      :key confluence-space-key
      :url (str "https://finosfoundation.atlassian.net/wiki/spaces/" confluence-space-key "/overview")
    }))

(defn- program-activities-metadata
  "A seq containing the metadata of all activities in the given program."
  [program]
  (let [program-id (:program-id program)]
    (seq
      (remove nil?
        (map #(if-let [activity (read-metadata-file (str program-metadata-directory "/" program-id "/" % "/" activity-filename))]
                (assoc activity
                       :program-id              program-id
                       :program-name            (:program-name program)
                       :program-short-name      (:program-short-name program)
                       :activity-id             %
                       :tags                    (if-let [current-tags (:tags activity)]      ; Normalise tags to lower case, de-dupe and sort
                                                  (seq (sort (distinct (map s/lower-case (remove s/blank? current-tags))))))
                       :lead-or-chair-person-id (:lead-or-chair activity)
                       :lead-or-chair           (person-metadata (:lead-or-chair activity))
                       :github-urls             (program-activity-github-urls program activity)
                       :mailing-lists           (map expand-mailing-list-address (:mailing-list-addresses activity))
                       :confluence-spaces       (map expand-confluence-space-key (:confluence-space-keys activity))))
             (program-activities program-id))))))

(defn- program-metadata-fn
  "Program metadata of the given program-id, or nil if there is none."
  [program-id]
  (if-let [program (read-metadata-file (str program-metadata-directory "/" program-id "/" program-filename))]
    (let [program (assoc program :program-id program-id)]   ; Note: this assoc has to happen first, since (program-activities-metadata) depends on it.
      (assoc program
             :github-url               (if (:github-org program) (str "https://github.com/" (:github-org program)))
             :pmc-github-urls          (pmc-github-urls program)
             :activities               (program-activities-metadata program)
             :pmc-mailing-list         (expand-mailing-list-address (:pmc-mailing-list-address         program))
             :pmc-private-mailing-list (expand-mailing-list-address (:pmc-private-mailing-list-address program))
             :program-mailing-list     (expand-mailing-list-address (:program-mailing-list-address     program))
             :confluence-space         (expand-confluence-space-key (:confluence-space-key             program))))))
(def program-metadata (memoize program-metadata-fn))

(defn programs-metadata
  "A seq containing the metadata of all programs."
  []
  (remove nil? (map program-metadata programs)))

(defn activities-metadata
  "A seq containing the metadata of all activities, regardless of program."
  []
  (sort-by :activity-name (remove nil? (mapcat :activities (programs-metadata)))))

(defn activity-metadata
  "The metadata for a specific activity."
  [activity-id]
  (filter #(= activity-id (:activity-id %)) activities-metadata))

(defn- activity-metadata-by-name-fn
  [activity-name]
  (if-not (s/blank? activity-name)
    (if-let [result (first (filter #(= activity-name (:activity-name %)) (activities-metadata)))]
      result
      (log/warn "Could not find metadata for" activity-name))))
(def activity-metadata-by-name
  "The metadata for a specific activity, identified by name."
  (memoize activity-metadata-by-name-fn))

(defn projects-metadata
  "A seq containing the metadata of all activities of type PROJECT, regardless of program."
  []
  (filter #(= (:type %) "PROJECT") (activities-metadata)))

(defn working-groups-metadata
  "A seq containing the metadata of all activities of type WORKING_GROUP, regardless of program."
  []
  (filter #(= (:type %) "WORKING_GROUP") (activities-metadata)))

(defn- current?
  "True if the given 'date range' map (with a :start-date and/or :end-date key) is current i.e. spans today."
  [m]
  (if m
    (let [today      (tf/unparse (tf/formatters :date) (tm/now))
          start-date (:start-date m)
          end-date   (:end-date   m)]
      (and (or (nil? start-date) (neg? (compare start-date today)))
           (or (nil? end-date)   (neg? (compare today end-date)))))))

(defn current-approved-contributors
  "A seq of person metadata for the *currently* approved contributors for the given organization-id, or nil if there are none."
  [organization-id]
  (if-let [organization-metadata (organization-metadata organization-id)]
    (if-let [current-contributors (seq (filter current? (:approved-contributors organization-metadata)))]
      (map #(person-metadata (:person-id %)) current-contributors))))

(defn current-affiliations
  "A seq of organization metadata the given person-id is *currently* affiliated with, or nil if there are none."
  [person-id]
  (if-let [person-metadata (person-metadata person-id)]
    (if-let [current-affiliations (seq (filter current? (:affiliations person-metadata)))]
      (map #(organization-metadata (:organization-id %)) current-affiliations))))

(defn assoc-org-name
  [person]
  (let [id (:person-id person)]
    (assoc person :org-name (or (str " (" (:organization-name (first (current-affiliations id))) ")") ""))))

(defn orgs-in-pmc
  [program]
  (let [pmc-list (:pmc program)]
    (distinct (remove #(= "Individual Contributor" %) (map #(:organization-name (first (current-affiliations %))) pmc-list)))))

(defn activities
  [program type]
  (let [program-id (:id program)]
  (map #(:activity-name %)
        (remove #(= "ARCHIVED" (:state %))
          (filter #(= type (:type %))
            (:activities program))))))

(defn pmc-lead
  [program]
  (let [pmc-lead       (:pmc-lead program)
        lead-enriched  (person-metadata pmc-lead)
        full-name      (:full-name lead-enriched)
        org-name       (:org-name (assoc-org-name lead-enriched))]
    (str full-name org-name)))

(defn pmc-list
  [program]
  (let [pmc-list        (:pmc program)
        people-enriched (map #(person-metadata %) pmc-list)
        orgs-enriched   (map #(assoc-org-name %) people-enriched)]
    (map #(str (:full-name %) (:org-name %)) orgs-enriched)))
            
(defn has-icla?
  [person-id]
  (boolean (:has-icla (person-metadata person-id))))

(defn has-ccla?
  [person-id]
  (if-let [current-affiliations-with-cclas (seq (filter :has-ccla (current-affiliations person-id)))]
    (let [current-approved-contributors (map :person-id
                                             (mapcat #(current-approved-contributors (:organization-id %))
                                                     current-affiliations-with-cclas))]
      (or (empty? current-approved-contributors)
          (boolean (some #{person-id} current-approved-contributors))))
    false))

(defn has-cla?
  [person-id]
  (or (has-icla? person-id)
      (has-ccla? person-id)))

(defn people-with-clas
  "A seq of person metadata for all people who currently have CLAs with the Foundation."
  []
  (map person-metadata (filter has-cla? people)))

(defn all-activity-tags
  "A seq of all of the tags in activities, normalised to lower-case."
  []
  (seq (sort (distinct (map s/lower-case (remove s/blank? (mapcat :tags (activities-metadata))))))))
