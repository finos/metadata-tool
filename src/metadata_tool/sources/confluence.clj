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
(ns metadata-tool.sources.confluence
  (:require [clojure.string                :as str]
            [clj-http.client               :as http]
            [clj-http.conn-mgr             :as conn]
            [metadata-tool.tools.selenium  :as selenium]
            [metadata-tool.config          :as cfg])
  (:import
   (org.openqa.selenium By)
   (org.openqa.selenium.support.ui ExpectedConditions WebDriverWait)))

(def selenium-timeout 5)

(def cm (conn/make-reusable-conn-manager {}))

(defn client*
  "Creates a new caching HTTP client"
  []
  (:http-client
   (http/get (:host (:confluence cfg/config))
             {:connection-manager cm
              :cache true})))

(def ^{:arglists '([])} client
  "Creates (if needed) and returns a singleton instance of a caching HTTP client"
  (memoize client*))

(defn cget
  "Invokes the Confluence GET REST API identified by the given URL substrings"
  [& args]
  (let [host (:host (:confluence cfg/config))
        url    (str host "/wiki/rest/api/" (str/join args))]
    (http/get url {:connection-manager cm
                   :http-client        (client)
                   :cache              true
                   :as                 :json})))

(defn page-id
  [url]
  (nth
   (str/split url #"/") 4))

(defn content
  [path]
  (try
    (let [driver (selenium/init-driver)
          wdw (WebDriverWait. driver selenium-timeout)
          condition (ExpectedConditions/elementToBeClickable (By/id "main-content"))
          host (:host (:confluence cfg/config))
          url (str host "/wiki" path)]
      (.get driver url)
      (.until wdw condition)
      (-> driver
        (.findElement (By/xpath "//*[self::h1 or self::h2 and text()='Attendees']/following::table"))
        (.getAttribute "innerHTML")))
  (catch Exception e 
    (println "Error parsing -" (str (:host (:confluence cfg/config)) "/wiki" path))
    (println (.getMessage e)))))

(defn children
  [id]
  (try
    (:results (:body (cget "content/" id "/child/page")))
    (catch Exception e [])))
