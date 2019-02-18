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
    (:require [clojure.string        :as s]
              [clj-http.client       :as http]
              [metadata-tool.config  :as cfg]
              ))

(def host "https://finosfoundation.atlassian.net")

(defn cget [& args]
    (http/get (str host "/wiki/rest/api/" (apply str args))
        {:basic-auth [(:username (:confluence cfg/config)) (:password (:confluence cfg/config))]}))

(defn pageTitle
    [id]
    (:title (:body (cget "content/" id))))

; (metadata-tool.sources.confluence/pageId metadata-tool.sources.confluence/url)
(defn pageId
    [url]
    (nth 
        (s/split (s/replace url host "") #"/")
        5))

; (metadata-tool.sources.confluence/meetingRoster (metadata-tool.sources.confluence/pageId metadata-tool.sources.confluence/url))
(defn meetingRoster
    [id]
    ; TODO - Invoke HTML parsing here
    (:value (:storage (:body (:body 
        (cget "content/" id "?expand=body.storage"))))))

; (metadata-tool.sources.confluence/children (metadata-tool.sources.confluence/pageId metadata-tool.sources.confluence/url))
; (metadata-tool.sources.confluence/children "notn")
(defn children
    [id]
    (try
        (:results (:body (cget "content/" id "/child/page")))
        ; TODO - check Exception status code (clj-http)
        ; tried with (:status (:data e))
        (catch Exception e [])))

(defn idAndTitle
    [payload]
    {:id (:id payload) :title (:title payload)})

; (metadata-tool.sources.confluence/meetingsIds (metadata-tool.sources.confluence/pageId metadata-tool.sources.confluence/url))
(defn meetingsIds
    [id]
    (let [children (map #(idAndTitle %) (children id))]
            (flatten (concat 
                children
                (map #(meetingsIds (:id %)) children)))))