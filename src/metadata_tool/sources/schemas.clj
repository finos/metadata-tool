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
(ns metadata-tool.sources.schemas
  (:require [clojure.string               :as s]
            [clojure.tools.logging        :as log]
            [clojure.java.io              :as io]
            [mount.core                   :as mnt :refer [defstate]]
            [scjsv.core                   :as jsv]
            [metadata-tool.config         :as cfg]
            [metadata-tool.sources.github :as gh]))

(defstate schema-directories
  "The available types of schema, and the location of their version-specific schema definition files within the metadata repository."
  :start { :organization (str gh/metadata-directory "/jsonschema/organization-metadata")
           :person       (str gh/metadata-directory "/jsonschema/person-metadata")
           :program      (str gh/metadata-directory "/jsonschema/program-metadata")
           :activity     (str gh/metadata-directory "/jsonschema/activity-metadata")
           :repository   (str gh/metadata-directory "/jsonschema/repository-metadata")
         })

(defstate schema-ids
  "Set of all available schema types and versions."
  :start (let [schema-types (keys schema-directories)]
           (set (for [schema-type schema-types
                      version     (map #(.getName ^java.io.File %)
                                       (.listFiles (io/file (get schema-directories schema-type))))]
                  [schema-type version]))))

(defn- load-schema
  "Loads a specific schema-id (a two element vector of type and version)."
  [[schema-type version :as schema-id]]
  (let [schema-file-path (str (schema-type schema-directories) "/" version)]
    (jsv/json-validator (slurp schema-file-path))))

(defstate schema-validators
  "Map of schema validation functions, in this format:
    {
      [:schema-type version-string] ...JSON String validator fn...
    }"
  :start (into {} (map #(hash-map % (load-schema %)) schema-ids)))

(defn validate-json
  "Validates the given JSON string against the given 'schema-id' (a two element vector such as [:person \"1.0.0\"]).
  Returns nil on success, or a status map on error (see https://github.com/metosin/scjsv for details)."
  [schema-id ^String json]
  (if-let [validator-fn (get schema-validators schema-id)]
    (if-let [validation-result (validator-fn json)]   ; scjsv uses nil to indicate failure - we convert that to an exception...
      (throw (Exception. (s/join "\n" (map :message validation-result)))))
    (throw (Exception. (str schema-id " is an unknown schema id.")))))
