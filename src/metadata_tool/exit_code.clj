;
; Copyright 2018 Fintech Open Source Foundation
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

(ns metadata-tool.exit-code)

(def ^:private exit-code (atom 0))

(defn set-exit-code
  "Sets the exit code to at least the given value."
  [new-exit-code]
  (swap! exit-code max new-exit-code))

(defn set-warning
  "Set a warning exit code (1)."
  []
  (set-exit-code 1))

(defn set-error
  "Set an error exit code (2)."
  []
  (set-exit-code 2))

(defn get-exit-code
  "Retrieves the current value of the exit code."
  []
  @exit-code)

