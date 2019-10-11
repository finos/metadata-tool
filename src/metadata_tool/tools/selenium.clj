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
    The entry point is `init-driver-pool`"
    (:import
     [org.openqa.selenium.chrome ChromeDriver ChromeOptions]
     [org.openqa.selenium Proxy]))

(def driver-pool (atom []))

(defn web-driver
  "Creates a web driver for Chrome using `options`.
  Defaults to a Firefox driver is there is no matching keyword."
  [options]
  (ChromeDriver. options))

(defmethod ->driver-options [options]
  (System/setProperty "webdriver.chrome.silentLogging" "true")
  (System/setProperty "webdriver.chrome.silentOutput" "true")
  (let [default-options (-> (ChromeOptions.)
                            (.setHeadless true))]
    (if-let [proxy (:proxy options)]
      (->> (->proxy proxy proxy)
            (.setProxy default-options))
      default-options)))

(defn init-driver
  "Initialises a new web driver with `options` and stores it in the driver pool."
  [options]
  (let [opts (->driver-options options)
        driver (web-driver opts)]
    (swap! driver-pool conj driver)
    driver))

(defn init-driver-pool
  "Initialises the driver pool of `pool-size` and using `driver-options`.
  `pool-size` defaults to 5 is not present."
  [{:keys [driver-options pool-size] :or {pool-size driver-pool-size}}]
  (->> (repeatedly pool-size #(init-driver driver-options))
        (reset! driver-pool)))

(defn get-meeting-page
  "Scrapes the public confluence page"
)
  