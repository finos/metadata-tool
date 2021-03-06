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
(ns metadata-tool.main
  (:require [clojure.string          :as str]
            [clojure.java.io         :as io]
            [clojure.tools.cli       :as cli]
            [clojure.tools.logging   :as log]
            [aero.core               :as a]
            [mount.core              :as mnt :refer [defstate]]
            [metadata-tool.exit-code :as ec]
            [metadata-tool.core      :as c])
  (:gen-class))

(def ^:private cli-opts
  [["-c" "--config-file FILE" "Path of configuration file (optional, defaults to 'config.edn' in the classpath)"
    :validate [#(.exists  (io/file %)) "Must exist"
               #(.isFile  (io/file %)) "Must be a file"
               #(.canRead (io/file %)) "Must be readable"]]
   ["-m" "--metadata-directory DIRECTORY" "Path of local metadata directory (optional, metadata will be checked out from GitHub if not specified)"
    :validate [#(.exists      (io/file %)) "Must exist"
               #(.isDirectory (io/file %)) "Must be a directory"
               #(.canRead     (io/file %)) "Must be readable"]]
   ["-p" "--projects-directory DIRECTORY" "Path of local (toplevel) projects metadata directory (optional, metadata will be checked out from GitHub if not specified)"]
   ["-r" "--github-revision REVISION" "GitHub revision of the metadata repository to checkout and use (optional, defaults to latest)"]
   [nil  "--email-override" "Overrides the default email behaviour of using a test email address for all outbound emails (DO NOT USE UNLESS YOU REALLY KNOW WHAT YOU'RE DOING!)."]
   ["-h" "--help"]])

(defn- usage
  [options-summary]
  (str/join
   \newline
   ["Runs one or more metadata tools."
    ""
    "Usage: metadata-tool [options] tool [tool] ..."
    ""
    "Options:"
    options-summary
    ""
    (str "Available tools:\n\t" (str/join "\n\t" c/tool-names))]))

(defn- error-message
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn- exit
  ([exit-code] (exit exit-code nil))
  ([exit-code message]
   (ec/set-exit-code exit-code)
   (when-not (str/blank? message)
     (println message))
   (flush)
   (System/exit (ec/get-exit-code))))

(defn- get-meetings-config
  [options]
  (let [path (str (:metadata-directory options) "/meeting-crawler.edn")]
    (if (.exists (io/file path))
      (a/read-config path))))

(defn- get-invite-config
  [options]
  (let [path (str (:metadata-directory options) "/gh-org-invite.edn")]
    (if (.exists (io/file path))
      (a/read-config path))))

(defn -main
  [& args]
  (log/info "metadata-tool started")
  (try
    (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)
          invite-config (get-invite-config options)]
      (cond
        (:help options)    (exit 0 (usage summary))
        (empty? arguments) (exit 1 (usage summary))
        errors             (exit 1 (error-message errors))
        :else              (mnt/with-args (assoc (if-let [config-file (:config-file options)]
                                                   (a/read-config config-file)
                                                   (a/read-config (io/resource "config.edn")))
                                                 :metadata-directory (:metadata-directory options)
                                                 :projects-directory (:projects-directory options)
                                                 :meetings (get-meetings-config options)
                                                 :skip-invites       (:skip-invitations invite-config)
                                                 :enable-auto-invite (:enabled invite-config)
                                                 :github-revision    (:github-revision    options)
                                                 :email-override     (boolean (:email-override options)))))
      (let [tools-to-run (map str/lower-case arguments)]
        (if (every? (set c/tool-names) tools-to-run)
          (try
            (mnt/start)
            (doall (map #(do (log/info "Running tool" %)
                             (c/run-tool %)
                             (flush))
                        arguments))
            (finally
              (mnt/stop)))
          (exit 1 (str "Unknown tool - available tools are:\n\t" (str/join "\n\t" c/tool-names))))))
    (catch Exception e
      (log/error "metadata-tool finished unsuccessfully" e)
      (exit 2)))
  (log/info "metadata-tool finished successfully")
  (exit 0))
