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

(defn- collaborators-fn
  "Returns the collaborators for the given repo, or for all repos if none is provided."
  [repo-url]
  (let [[org repo] (remove s/blank? (s/split (:path (uri/uri repo-url)) #"/"))]
    (remove #(some #{(:login %)} org-admins) (tr/collaborators org repo opts))))
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









(comment
(defn- check-call
  [gh-result & [url fail-silently]]
  (if-let [response-code (:status gh-result)]
    (if (and (>= response-code 400)
             (not fail-silently))
      (throw (Exception. (str response-code
                              " response from GitHub: "
                              (:message (:body gh-result))
                              (if url (str " URL: " url)))))))
  gh-result)

(def user-details
  "Returns user details for the given Github username."
  (memoize tu/user))

(defn- repos-fn
  []
  (check-call (tr/org-repos org-name opts) (str "org-repos/" org-name)))

(def repos
  "Returns all repos in the Foundation org."
  (memoize repos-fn))

(defn- repo-hotness
  "Returns the 'hotness' of the given repo"
  [repo]
  (+ (* (get repo :collaborators 0) 5)
     (* (get repo :forks         0) 4)
     (* (get repo :stars         0) 2)
     (* (get repo :watchers      0) 1)))

(defn repo-langs
  "Returns a list containing the names of all languages in a given repo."
  [repo]
  (let [repo-name (get repo "repositoryName")
        url       (str "languages/" repo-name)
        langs     (check-call (tr/languages org-name repo-name opts) url true)]
    langs))

(defn repo-stats
  "Returns GitHub repo stats."
  [repo-name]
  (let [repo-meta   (check-call (tr/specific-repo org-name repo-name opts) (str "specific-repo/" org-name "/" repo-name) true)
        collabs     (check-call (tr/collaborators org-name repo-name opts) (str "collaborators/" org-name "/" repo-name) true)
        repo-stats  (assoc {} :collaborators (count collabs)
                              :stars         (:stargazers_count repo-meta)
                              :watchers      (:watchers_count   repo-meta)
                              :size          (:size             repo-meta)
                              :forks         (:forks_count      repo-meta))]
    (assoc repo-stats :hotness (repo-hotness repo-stats))))

(defn repo-names
  "Returns a list containing the names of all repos in the SSF org."
  []
  (map :name (repos)))

(defn- ls-fn
  ([dir]      (ls-fn metadata-repo-name dir))
  ([repo dir] (map :name (check-call (tr/contents org-name repo dir opts)))))
(def ls
  "Lists the contents of the given directory in the metadata repository, or (2 param version) the given repository."
  (memoize ls-fn))

(defn- read-file-fn
  ([path]      (read-file-fn metadata-repo-name path))
  ([repo path] (:content (check-call (tr/contents org-name repo path (assoc opts :str? true))))))
(def read-file
  "Reads the contents of the given file in the metadata repository, or (2 param version) the given repository."
  (memoize read-file-fn))
)