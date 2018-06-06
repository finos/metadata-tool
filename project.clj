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
  :repositories         [
                          ["sonatype-snapshots" {:url "https://oss.sonatype.org/content/groups/public" :snapshots true}]
                          ["jitpack"            {:url "https://jitpack.io"                             :snapshots true}]
                        ]
  :plugins              [
                          [lein-licenses "0.2.2"]
                        ]
  :dependencies         [
                          [org.clojure/clojure                   "1.9.0"]
                          [org.clojure/tools.cli                 "0.3.7"]
                          [org.clojure/tools.logging             "0.4.1"]
                          [ch.qos.logback/logback-classic        "1.2.3"]
                          [org.slf4j/jcl-over-slf4j              "1.7.25"]
                          [org.slf4j/log4j-over-slf4j            "1.7.25"]
                          [org.slf4j/jul-to-slf4j                "1.7.25"]
                          [cheshire                              "5.8.0"]
                          [aero                                  "1.1.3"]
                          [mount                                 "0.1.12"]
                          [lambdaisland/uri                      "1.1.0"]
                          [com.github.grinnbearit/freemarker-clj "-SNAPSHOT"]
                          [metosin/scjsv                         "0.4.1"]
                          [clj-jgit                              "0.8.10" :exclusions [org.apache.httpcomponents/httpclient]]
                          [irresponsible/tentacles               "0.6.2"]
                          [cc.qbits/spandex                      "0.6.2" :exclusions [commons-logging org.apache.httpcomponents/httpcore-nio]]
                          [com.draines/postal                    "2.0.2" :exclusions [commons-codec]]
                        ]
  :managed-dependencies [
                          ; The following dependencies are inherited but have conflicting or old versions, so we "pin" the versions here
                          [joda-time/joda-time "2.10"]
                          [clj-http            "3.9.0"]
                        ]
  :profiles             {
                          :dev     {:dependencies [[midje      "1.9.1"]]
                                    :plugins      [[lein-midje "3.2.1"]]}
                          :uberjar {:aot :all}
                        }
  :main ^:skip-aot metadata-tool.main)
