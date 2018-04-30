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

(ns metadata-tool.config
  (:require [clojure.string  :as s]
            [clojure.java.io :as io]
            [mount.core      :as mnt :refer [defstate]]))

; Because java.util.logging is a hot mess
(org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
(org.slf4j.bridge.SLF4JBridgeHandler/install)

(defstate config
          :start (mnt/args))

(defstate temp-directory
          :start (let [result      (if (s/blank? (:temp-dir config))
                                     (System/getProperty "java.io.tmpdir")
                                     (:temp-dir config))
                       result-as-f (io/file result)]
                   (if (.exists result-as-f)
                     (if (.isDirectory result-as-f)
                       (if (.canWrite result-as-f)
                         result
                         (throw (Exception. (str "Temp directory " result " is not writable."))))
                       (throw (Exception. (str "Temp directory " result " is not a directory."))))
                     (throw (Exception. (str "Temp directory " result " does not exist."))))))

