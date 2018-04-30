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

(ns metadata-tool.template
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [mount.core            :as mnt :refer [defstate]]
            [freemarker-clj.core   :as ftl]))

(def ^:private template-path "/templates/")

(defstate freemarker-config
          :start (doto ^freemarker.template.Configuration (ftl/gen-config)
                   (.setClassLoaderForTemplateLoading (.getContextClassLoader (Thread/currentThread))
                                                      template-path)
                   (.setDefaultEncoding "UTF-8")
                   (.setLocale (java.util.Locale/US))))

(defn render
  "Renders the data (as a map) using the named template file (which must exist in the templates folder), returning the rendered result as a String."
  [^String t ^java.util.Map d]
  (ftl/render freemarker-config t d))
