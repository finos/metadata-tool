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
(ns metadata-tool.sources.github
  (:require [clojure.string        :as str]
            [clojure.java.io       :as io]
            [clojure.set           :as set]
            [clojure.tools.logging :as log]
            [mount.core            :as mnt :refer [defstate]]
            [lambdaisland.uri      :as uri]
            [clj-jgit.porcelain    :as git]
            [clj-http.client       :as http]
            [clojure.data.json     :as json]
            [tentacles.repos       :as tr]
            [tentacles.search      :as ts]
            [tentacles.issues      :as ti]
            [tentacles.orgs        :as to]
            [tentacles.users       :as tu]
            [metadata-tool.config  :as cfg]))

(def ^:private metadata-org-name  "finos")
(def ^:private metadata-repo-name "metadata")
(def ^:private org-admins         #{"ssf-admin" "finos-admin"})
(def ^:private ignored-repo-names #{"clabot-config"})

(defn- rm-rf
  [^java.io.File file]
  (when (.isDirectory file)
    (doseq [child-file (.listFiles file)]
      (rm-rf child-file)))
  (io/delete-file file true))


; ####TODO: ALLOW USE OF GITHUB TOKEN!!!


(defstate username
  :start (:username (:github cfg/config)))

(defstate password
  :start (:password (:github cfg/config)))

(defstate auth
  :start (str username ":" password))

(defstate opts
  :start {:throw-exceptions true :all-pages true :per-page 100 :auth auth :user-agent (str metadata-org-name " metadata tool")})

(defstate github-revision
  :start (:github-revision cfg/config))

(defstate projects-directory
  :start (if-not (str/blank? (:projects-directory cfg/config))
           (do
             (log/info "Using local projects metadata directory at" (:projects-directory cfg/config))
             (:projects-directory cfg/config))))

(defstate metadata-directory
  :start (if-not (str/blank? (:metadata-directory cfg/config))
           (do
             (log/info "Using local metadata directory at" (:metadata-directory cfg/config))
             (:metadata-directory cfg/config))
           (let [result (str cfg/temp-directory
                             (if (not (str/ends-with? cfg/temp-directory "/")) "/")
                             "finos-metadata-" (java.util.UUID/randomUUID))
                 _      (log/info "Cloning metadata repository to" result)
                 repo   (git/with-credentials ^String username
                          ^String password
                          (git/git-clone (str "https://github.com/" metadata-org-name "/" metadata-repo-name) result))]
             (when-not (str/blank? github-revision)
               (log/info "Checking out revision" github-revision)
               (git/git-checkout repo github-revision))
             (rm-rf (io/file (str result "/.git/")))    ; De-gitify the local clone so we can't accidentally mess with it
             result))
  :stop  (if (not= metadata-directory (:metadata-directory cfg/config))
           (rm-rf (io/file metadata-directory))))

; Note: functions that call GitHub APIs are memoized, so that when tools are "stacked" they benefit from cached GitHub API calls

(defn- parse-github-url-path
  "Parses the path elements of a GitHub URL - useful for retrieving org name (first position) and repo name (optional second position)."
  [url]
  (if-not (str/blank? url)
    (remove str/blank? (str/split (:path (uri/uri url)) #"/"))))

(defmacro ^:private call-gh
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo ei#
       (if-not (= 404 (:status (ex-data ei#)))
         (throw ei#)))))

(defn user-id-fn
  "Returns the ID of a given GitHub username"
  [username]
  (let [new-opts (assoc opts :basic-auth (:auth opts))
        url      (str "https://api.github.com/users/" username)]
    (if-let [payload (call-gh (:body (http/get url new-opts)))]
      (get (json/read-str payload) "id"))))
    
(def user-id (memoize user-id-fn))

(defn content-fn
  "Returns the contents of file"
  [org repo path]
  (call-gh
    (:body (http/get
     (str "https://raw.githubusercontent.com/" org "/" repo "/master/" path)))))
(def content (memoize content-fn))

(defn folder-fn
  "Returns the metadata of a folder in GitHub"
  [org repo path]
  (call-gh
    (:body (http/get
     (str "https://api.github.com/repos/" org "/" repo "/contents/" path)))))
(def folder (memoize folder-fn))

(defn pending-invitations-fn
  "Returns the list of pending invitations for a given org"
  [org-name]
  (call-gh
   (let [new-opts (assoc opts :basic-auth (:auth opts))
         url (str "https://api.github.com/orgs/" org-name "/invitations")]
    (json/read-str (call-gh (:body (http/get url new-opts)))))))
(def pending-invitations (memoize pending-invitations-fn))

(defn invite-member
  "Invites a github user to a given org"
  [org user]
    ; TODO - enable it widely, now only test user is enabled
    ;; (println "Inviting member " user "to org" org)
    (if (= "mammamao" user)
      (let [new-opts (assoc opts :basic-auth (:auth opts))
            url (str "https://api.github.com/orgs/" org "/memberships/" user)
            invite-resp (call-gh (:body (http/put url new-opts)))]
        (println "Invited user " user " to github " org " org"))))

(defn- collaborators-fn
  "Returns the collaborators for the given repo, or nil if the URL is invalid."
  [repo-url & [affiliation]]
  (log/debug "Requesting repository collaborators for" repo-url)
  (if-not (str/blank? repo-url)
    (let [[org repo] (parse-github-url-path repo-url)
          collab-opts (assoc opts :affiliation affiliation)]
      (if (and (not (str/blank? org))
               (not (str/blank? repo)))
        (remove #(some #{(:login %)} org-admins) (call-gh (tr/collaborators org repo collab-opts)))))))
(def collaborators (memoize collaborators-fn))

(defn- teams-fn
  "Returns the teams for the given repo, or nil if the URL is invalid."
  [repo-url]
  (log/debug "Requesting repository teams for" repo-url)
  (if-not (str/blank? repo-url)
    (let [[org repo] (parse-github-url-path repo-url)]
          (call-gh (tr/teams org repo opts)))))
(def teams (memoize teams-fn))

(defn- org-members-fn
  "Returns the members for the given org, or nil if the URL is invalid."
  [org-url]
  (log/debug "Requesting org members for" org-url)
  (if-not (str/blank? org-url)
    (let [[org-name] (parse-github-url-path org-url)]
          (call-gh (to/members org-name opts)))))
(def org-members (memoize org-members-fn))

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
  (map :login (admins repo-url)))

(defn- committers
  "List the committers (non-admin collaborators) of the given repository."
  [repo-url]
  (remove #(:admin (:permissions %))) (collaborators repo-url))

(defn committer-logins
  "List the logins of the committers of the given repository."
  [repo-url]
  (map :login (committers repo-url)))

(defn issue-commenters-fn
  "Returns all usernames who commented on a given issue"
  [issue-number org repo]
  (set/difference
   (set (map #(:login (:user %))
        (ti/issue-comments org repo issue-number opts)))
   #{"finos-admin" "github-actions[bot]"}))
(def issue-commenters (memoize issue-commenters-fn))

(defn issues-fn
  "Returns first 100 open issues, with a given label, across all repos of a given org"
  [org-name label]
  (let [issues (:items (ts/search-issues ""
                                 {:label (str "\"" label "\"")
                                  :state "open"
                                  :org org-name} opts))]
    issues))
(def issues (memoize issues-fn))

(defn- org-fn
  "Returns information on the given org, or nil if it doesn't exist."
  [org-url]
  (log/debug "Requesting org information for" org-url)
  (if-not (str/blank? org-url)
    (let [[org-name] (parse-github-url-path org-url)]
      (if-not (str/blank? org-name)
        (call-gh (to/specific-org org-name opts))))))
(def org (memoize org-fn))

(defn- repos-fn
  "Returns all public repos in the given org, or nil if there aren't any."
  [org-url]
  (log/debug "Requesting repositories for" org-url)
  (if-not (str/blank? org-url)
    (let [[org-name] (parse-github-url-path org-url)]
      (if-not (str/blank? org-name)
        (remove #(some #{(:name %)} ignored-repo-names) (remove :private (call-gh (tr/org-repos org-name opts))))))))
(def repos (memoize repos-fn))

(defn- filter-repo
  [repo filters]
  (if (empty? filters)
    true
    (let [filter (seq (first filters))
          curr   (= (get repo (first filter))
                    (second filter))
          tail (filter-repo repo (rest filters))]
      (and curr tail))))

(defn repos-urls
  "Returns the URLs of all repos in the given org."
  [org-url & [filters]]
  (if org-url
    (let [repos (repos org-url)]
      (map :html_url
           (filter #(filter-repo % filters) 
                   repos)))))

(defn- repo-fn
  "Retrieve the data for a specific public repo, or nil if it's private or invalid."
  [repo-url]
  (log/debug "Requesting repository details for" repo-url)
  (if repo-url
    (let [[org repo] (parse-github-url-path repo-url)]
      (if (and (not (str/blank? org))
               (not (str/blank? repo)))
        (let [result (call-gh (tr/specific-repo org repo opts))]
          (if-not (:private result)
            result))))))
(def repo (memoize repo-fn))

(defn- languages-fn
  "Retrieve the languages data for a specific repo."
  [repo-url]
  (log/debug "Requesting repository languages for" repo-url)
  (if repo-url
    (let [[org repo] (parse-github-url-path repo-url)]
      (if (and (not (str/blank? org))
               (not (str/blank? repo)))
        (call-gh (tr/languages org repo opts))))))
(def languages (memoize languages-fn))

(defn- user-fn
  "Retrieve the data for a specific GitHub username, or nil if it's invalid."
  [username]
  (if username
    (call-gh (tu/user username opts))))
(def user (memoize user-fn))
