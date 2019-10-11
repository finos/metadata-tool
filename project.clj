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

(defproject org.finos/metadata-tool "0.1.0-SNAPSHOT"
  :description          "A simple tool that provides various functions on Foundation metadata."
  :url                  "https://github.com/finos/metadata-tool"
  :license              {:name "Apache License, Version 2.0"
                         :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version     "2.7.1"
  :repositories         [["sonatype-snapshots" {:url "https://oss.sonatype.org/content/groups/public" :snapshots true}]
                         ["jitpack"            {:url "https://jitpack.io"                             :snapshots true}]]
  :plugins              [[lein-licenses "0.2.2"]
                         [lein-ancient "0.6.15"]]
  :dependencies         [[org.clojure/clojure                   "1.10.1"]
                         [org.clojure/tools.cli                 "0.4.2"]
                         [org.clojure/tools.logging             "0.5.0"]
                         [ch.qos.logback/logback-classic        "1.2.3"]
                         [org.slf4j/jcl-over-slf4j              "1.7.28"]
                         [org.slf4j/log4j-over-slf4j            "1.7.28"]
                         [org.slf4j/jul-to-slf4j                "1.7.28"]
                         [cheshire                              "5.9.0"]
                         [aero                                  "1.1.3"]
                         [org.clojure/data.csv                  "0.1.4"]
                         [mount                                 "0.1.16"]
                         [enlive                                "1.1.6"]
                         [hickory                               "0.7.1"]
                         [lambdaisland/uri                      "1.1.0"]
                         [com.github.grinnbearit/freemarker-clj "-SNAPSHOT"]
                         [metosin/scjsv                         "0.5.0"]
                         [clj-jgit                              "0.8.10" :exclusions [org.apache.httpcomponents/httpclient]]
                         [irresponsible/tentacles               "0.6.4"]
                         [cc.qbits/spandex                      "0.7.1" :exclusions [commons-logging org.apache.httpcomponents/httpcore-nio]]
                         [com.draines/postal                    "2.0.3" :exclusions [commons-codec]]
                         [org.seleniumhq.selenium/selenium-server "3.141.59"]
                         [org.seleniumhq.selenium/selenium-api "3.141.59"]
                         [org.seleniumhq.selenium/htmlunit-driver "2.36.0"]]
  :managed-dependencies [; The following dependencies are inherited but have conflicting or old versions, so we "pin" the versions here
                         [joda-time/joda-time "2.10.4"]
                         [clj-http            "3.10.0"]]
  :profiles             {:dev     {:dependencies [[midje      "1.9.9"]]
                                   :plugins      [[lein-midje "3.2.1"]]}
                         :uberjar {:aot :all}}
  :main                 ^:skip-aot metadata-tool.main)
