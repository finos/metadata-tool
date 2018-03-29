;
; Copyright © 2017 FINOS Foundation
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
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.stacktrace    :as st]
            [clojure.tools.cli     :as cli]
            [clojure.tools.logging :as log]
            [aero.core             :as a]
            [mount.core            :as mnt :refer [defstate]]
            [metadata-tool.config  :as cfg]
            [metadata-tool.core    :as c])
  (:gen-class))

(def ^:private cli-opts
  [["-c" "--config-file FILE" "Path to configuration file (optional, defaults to 'config.edn' in the classpath)"
    :validate [#(.exists  (io/file %)) "Must exist"
               #(.isFile  (io/file %)) "Must be a file"
               #(.canRead (io/file %)) "Must be readable"]]
   ["-r" "--github-revision REVISION" "GitHub revision of the metadata repository to checkout and use (optional, defaults to latest)"]
   ["-h" "--help"]])

(defn- usage
  [options-summary]
  (s/join
    \newline
    ["Runs one or more metadata tools."
     ""
     "Usage: metadata-tool [options] tool [tool] ..."
     ""
     "Options:"
     options-summary
     ""
     (str "Available tools:\n\t" (s/join "\n\t" c/tool-names))]))

(defn- error-message
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn- exit
  ([status-code] (exit status-code nil))
  ([status-code message]
   (when-not (s/blank? message)
     (println message)
     (flush))
   (System/exit status-code)))

(defn -main
  [& args]
  (log/debug "metadata-tool started")
  (try
    (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
      (cond
        (:help options)    (exit 0 (usage summary))
        (empty? arguments) (exit 1 (usage summary))
        errors             (exit 1 (error-message errors))
        :else              (mnt/with-args (assoc (if-let [config-file (:config-file options)]
                                                   (a/read-config config-file)
                                                   (a/read-config (io/resource "config.edn")))
                                                 :github-revision (:github-revision options))))
      (let [tools-to-run (map s/lower-case arguments)]
        (if (every? (set c/tool-names) tools-to-run)
          (try
            (log/debug "Running tools" (s/join ", " tools-to-run))
            (mnt/start)
            (doall (map c/run-tool arguments))
            (finally
              (mnt/stop)))
          (exit 1 (str "Unknown tool - available tools are:\n\t" (s/join "\n\t" c/tool-names))))))
    (catch Exception e
      (st/print-cause-trace e)
      (flush)
      (exit 2)))
  (log/debug "metadata-tool finished successfully")
  (exit 0))
