;
; Copyright © 2017 FINOS Foundation
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
(ns metadata-tool.sources.github
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.tools.logging :as log]
            [mount.core            :as mnt :refer [defstate]]
            [lambdaisland.uri      :as uri]
            [cheshire.core         :as ch]
            [clj-jgit.porcelain    :as git]
            [tentacles.core        :as tc]
            [tentacles.repos       :as tr]
            [tentacles.orgs        :as to]
            [tentacles.users       :as tu]
            [metadata-tool.config  :as cfg]))

(def ^:private org-name           "finos")
(def ^:private metadata-repo-name "metadata")
(def ^:private org-admins         #{"ssf-admin" "finos-admin"})

(defn- rm-rf
  [^java.io.File file]
  (when (.isDirectory file)
    (doseq [child-file (.listFiles file)]
      (rm-rf child-file)))
  (io/delete-file file true))

(defstate username
          :start (:username (:github cfg/config)))

(defstate password
          :start (:password (:github cfg/config)))

(defstate auth
          :start (str username ":" password))

(defstate opts
          :start {:throw-exceptions true :all-pages true :per-page 100 :auth auth :user-agent (str org-name " metadata tool")})

(defstate github-revision
          :start (:github-revision cfg/config))

(defstate metadata-directory
          :start (if-not (s/blank? (:metadata-directory cfg/config))
                   (do
                     (log/debug "Using local metadata directory at" (:metadata-directory cfg/config))
                     (:metadata-directory cfg/config))
                   (let [result (str cfg/temp-directory
                                       (if (not (s/ends-with? cfg/temp-directory "/")) "/")
                                       "finos-metadata-" (java.util.UUID/randomUUID))
                         _      (log/debug "Cloning metadata repository to" result)
                         repo   (git/with-credentials ^String username
                                                      ^String password
                                                      (git/git-clone (str "https://github.com/" org-name "/" metadata-repo-name) result))]
                     (when-not (s/blank? github-revision)
                       (log/debug "Checking out revision" github-revision)
                       (git/git-checkout repo github-revision))
                     (rm-rf (io/file (str result "/.git/")))    ; De-gitify the local clone so we can't accidentally mess with it
                     result))
          :stop  (if (not= metadata-directory (:metadata-directory cfg/config))
                   (rm-rf (io/file metadata-directory))))

; Note: functions that call GitHub APIs are memoized, so that when tools are "stacked" they benefit from cached GitHub API calls

(defn- parse-github-url-path
  "Parses the path elements of a GitHub URL - useful for retrieving org name (first position) and repo name (optional second position)."
  [url]
  (if-not (s/blank? url)
    (remove s/blank? (s/split (:path (uri/uri url)) #"/"))))


(defn- collaborators-fn
  "Returns the collaborators for the given repo, or for all repos if none is provided."
  [repo-url]
  (log/debug "Requesting repository collaborators for" repo-url)
  (if-not (s/blank? repo-url)
    (let [[org repo] (parse-github-url-path repo-url)]
      (if (and (not (s/blank? org))
               (not (s/blank? repo)))
        (remove #(some #{(:login %)} org-admins) (tr/collaborators org repo opts))))))
(def collaborators (memoize collaborators-fn))

(defn collaborator-logins
  "Returns a list containing the logins of all collaborators in the given repo, or for all repos if none is provided."
  [repo-url]
  (map :login (collaborators repo-url)))

(defn- admins
  "List the admins of the given repository."
  [repo-url]
  (filter #(:admin (:permissions %)) (collaborators repo-url)))

(defn admin-logins
  "List the logins of the admins of the given repository."
  [repo-url]
  (map :login (filter #(:admin (:permissions %)) (collaborators repo-url))))

(defn- repos-fn
  "Returns all repos in the given org."
  [org-url]
  (log/debug "Requesting repositories for" org-url)
  (if-not (s/blank? org-url)
    (let [[org-name] (parse-github-url-path org-url)]
      (if-not (s/blank? org-name)
        (tr/org-repos org-name opts)))))
(def repos (memoize repos-fn))

(defn repos-urls
  "Returns the URLs of all repos in the given org."
  [org-url]
  (if org-url
    (map :html_url (repos org-url))))

(defn- repo-fn
  "Retrieve the data for a specific repo."
  [repo-url]
  (log/debug "Requesting repository details for" repo-url)
  (if repo-url
    (let [[org repo] (parse-github-url-path repo-url)]
      (if (and (not (s/blank? org))
               (not (s/blank? repo)))
        (tr/specific-repo org repo opts)))))
(def repo (memoize repo-fn))


(defn- languages-fn
  "Retrieve the languages data for a specific repo."
  [repo-url]
  (log/debug "Requesting repository languages for" repo-url)
  (if repo-url
    (let [[org repo] (parse-github-url-path repo-url)]
      (if (and (not (s/blank? org))
               (not (s/blank? repo)))
        (tr/languages org repo opts)))))
(def languages (memoize languages-fn))

