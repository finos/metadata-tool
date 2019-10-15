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
(ns metadata-tool.tools.selenium
    "This namespace contains facilities to interact with web drivers.
    The entry point is `init-driver`"
    (:import
     [org.openqa.selenium.chrome ChromeDriver ChromeOptions]
     [org.openqa.selenium Proxy]))

(defn web-driver
  "Creates a web driver for Chrome using `options`.
  Defaults to a Firefox driver is there is no matching keyword."
  [options]
  (ChromeDriver. options))

(defn driver-options []
  (System/setProperty "webdriver.chrome.silentLogging" "false")
  (System/setProperty "webdriver.chrome.silentOutput" "false")
  (-> (ChromeOptions.) (.setHeadless true)))

(defn init-driver
  "Initialises a new web driver with `options`."
  []
  (let [opts (driver-options)
        driver (web-driver opts)]
    driver))