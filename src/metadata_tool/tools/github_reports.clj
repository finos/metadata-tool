(ns metadata-tool.tools.github_reports
  (:require [clojure.string                 :as str]
            [clojure.data.json              :as json]
            [clojure.set                    :as set]
            [clojure.walk                   :as wlk]
            [clojure.java.io                :as io]
            [clojure.pprint                 :as pp]
            [metadata-tool.sources.github   :as gh]
            [metadata-tool.sources.metadata :as md]))

(def notice-finos-terms (str/lower-case "This product includes software developed at the Fintech Open Source Foundation"))

(def finos-github-orgs ["finos" "finos-plexus" "HadoukenIO" "symphonyoss"])

; TODO - load this from metadata-tool
(def finos-states ["active" "incubating" "released" "archived"])

(def contributing-header "# contributing")

(def license-header "# license")

(def message-header
  (str "**NOTE!** This issue was automatically generated by a FINOS GitHub Action.\n\n"
       "We have found some repository configurations that should be changed in order to comply with FINOS Governance and bylaws, see the details below.\n\nList of fixes:\n"))

(def message-footer
  (str "This is a message generated by the FINOS [project compliance scanning action](https://finosfoundation.atlassian.net/wiki/spaces/FDX/pages/75530344/Code+Validation#CodeValidation-Projectcompliancescan).\n\n"
       "For any question, do not hesitate to contact @maoo and @mcleo-d or email [help@finos.org](mailto:help@finos.org). Thank you!"))

(def messages 
  {:has-admin "One or more admin collaborators were found in this GitHub repository.. FINOS Governance doesn't allow GitHub users to have Admin rights on repositories, therefore it must be removed."
   :has-user "One or more user collaborators were found in this GitHub repository. FINOS Governance only allows GitHub users to be added via Teams. Please remove it, therefore it must be removed."
   :no-teams "This GitHub repository does not grant permissions to any FINOS Team, although it should be configured to grant access to the program and project specific teams defined in [https://github.com/orgs/finos/teams](https://github.com/orgs/finos/teams). Please email help@finos.org and coordinate changes to the repository access permissions."
   :disabled-issues "This GitHub repository does not have GitHub Issues enabled; make sure that there is a documented way to submit questions, feature requests and other communications to the project team."
   :no-issue-templates "This GitHub repository does not use issue templates; please check the [issue template blueprints](https://github.com/finos/project-blueprint/tree/master/.github/ISSUE_TEMPLATE)."
   :no-contributing "`CONTRIBUTING.md` file is missing; check the [CONTRIBUTING.md template](https://github.com/finos/project-blueprint/blob/master/.github/CONTRIBUTING.md)."
   :no-code-conduct "`CODE_OF_CONDUCT.md` file is missing; check the [CODE_OF_CONDUCT.md template](https://github.com/finos/project-blueprint/blob/master/.github/CODE_OF_CONDUCT.md)."
   :notice-nok "`NOTICE` file is incomplete; check line 4 of the [NOTICE template](https://github.com/finos/project-blueprint/blob/master/NOTICE)."
   :no-notice "`NOTICE` file is missing; check the [NOTICE template](https://github.com/finos/project-blueprint/blob/master/NOTICE)."
   :no-readme "`README.md` file is missing; check the [README.md template](https://github.com/finos/project-blueprint/blob/master/README.template.md)."
   :no-description "This GitHub repository does not have a general description defined; the `Edit` button is seen when on the repositories main page, which is the `Code` tab."
   :is-archivable "This repository belongs to project `{{project-name}}` which is archived, therefore the GitHub repository is expected to be archived too. @finos-staff will get in touch with the project lead to sort it out."
   :readme-nok "`README.md` file is incomplete; check the [README.md template](https://github.com/finos/project-blueprint/blob/master/README.template.md) and make sure that `## Contributing` and `## License` sections exist."
   :no-badge "`README.md` file is missing the FINOS badge; check the [README.md template](https://github.com/finos/project-blueprint/blob/master/README.template.md) and make sure that it embeds one of SVG FINOS badges."
   :wrong-badge "Our internal records state that this project is in `{{project-state}}` state, whereas README.md states `{{readme-state}}`; make sure that `README.md` embeds the right FINOS badge."
   :repo-not-on-file "We don't have this repository on file. We will fix this issue on our side as soon as possible and keep you posted."
   :no-whitesource "WhiteSource configuration was not found; make sure that dependencies are scanned against security vulnerabilities. Read more on [the WhiteSource Wiki page](https://finosfoundation.atlassian.net/wiki/spaces/FDX/pages/75530440/WhiteSource)."})

(defn- get-config
  "Returns the config object, or nil, if string is empty"
  [config]
  (if (str/blank? config)
    nil
    (json/read-str config)))

(defn- get-project-state
  "Returns the FINOS badge state, given the README contents"
  [readme]
  (if-not (str/blank? readme)
    (if-let [match (set/intersection
                    (set finos-states)
                    (set (re-matches 
                          #"(?s).*/finos/contrib-toolbox(.*)master/images/badge-(.*).svg\)(?s).*"
                          readme)))]
      (first match))))

(defn- check-non-archived-repo
  "Returns validation for a given repo-name"
  [org repo-url repo project]
  (let [repo-name       (last (str/split repo-url #"/"))
        collaborators   (gh/collaborators repo-url "direct")
        teams           (gh/teams repo-url)
        has-admin       (not (empty?
                              (filter #(:admin (:permissions %))
                                      collaborators)))
        has-user        (not (empty?
                              (filter #(= "User" (:type %))
                                      collaborators)))
      ;;   Useful for debugging purposes
      ;;   config          (get-config
      ;;                    "{ \"ignore\" : [\"has-user\"]}")
        config          (get-config
                         (gh/content org repo-name ".finos-blueprint.json"))
        notice          (gh/content org repo-name "NOTICE")
        readme          (gh/content org repo-name "README.md")
        issue-templates (gh/folder org repo-name ".github/ISSUE_TEMPLATE")
        project-state   (:state project)
        readme-state    (get-project-state readme)
        contributing    (gh/content org repo-name ".github/CONTRIBUTING.md")
        code-conduct    (gh/content org repo-name ".github/CODE_OF_CONDUCT.md")
        ws-config       (gh/content org repo-name ".whitesource")
        notice-content  (if (empty? notice) nil (str/lower-case notice))
        readme-content  (if (empty? readme) nil (str/lower-case readme))
        validations     {:has-admin          has-admin
                         :has-user           has-user
                         :no-notice          (nil? notice-content)
                         :no-readme          (nil? readme-content)
                         :no-teams           (empty? teams)
                         :is-archivable      (and
                                              (:archived repo)
                                              (not (= "ARCHIVED" (:state project))))
                         :disabled-issues    (not (:has_issues repo))
                         :no-issue-templates (empty? issue-templates)
                         :no-contributing    (empty? contributing)
                         :no-code-conduct    (empty? code-conduct)
                         :no-description     (str/blank? (:description repo))
                         :no-whitesource     (empty? ws-config)
                         :no-badge           (nil? readme-state)
                         :repo-not-on-file   (nil? project-state)
                         :wrong-badge        (and
                                              (not (nil? readme-state))
                                              (not (nil? project-state))
                                              (not (= (str/lower-case project-state)
                                                      (str/lower-case readme-state))))
                         :notice-nok         (and (not (nil? notice-content))
                                                  (not (str/includes?
                                                        notice-content
                                                        notice-finos-terms)))
                         :readme-nok         (and (not (nil? readme-content))
                                                  (or
                                                   (not (str/includes?
                                                         readme-content
                                                         contributing-header))
                                                   (not (str/includes?
                                                         readme-content
                                                         license-header))))}]
    (if (nil? project)
      (println (str "ERROR! Repo "
                    org "/" repo-name 
                    " doesn't have any project in FINOS metadata! Skippping validation."))
      (println (str "Checked " (:activity-name project) 
                    " for " org "/" repo-name 
                    " in FINOS metadata!")))
    {:url (str "https://github.com/" org "/" repo-name)
     :org org
     :repo-name repo-name
     :ignore (get config "ignore")
     :validations validations
     :vars {:project-state project-state
            :readme-state (if (nil? readme-state) nil (str/upper-case readme-state))
            :project-name (:activity-name project)}}))

(defn- check-repo
  "Returns validation for a given repo-name"
  [org repo-url]
(let [repo      (gh/repo repo-url)
      repo-name (last (str/split repo-url #"/"))
      project   (md/activity-by-github-coords org repo-name)
      state     (:state project)
      archived  (:archived repo)]
  (if (not archived)
    (check-non-archived-repo org repo-url repo project)
    (if (not (= "ARCHIVED" state))
      (println "WARN. Repo" repo-url "is archived in GitHub but its state is" state "and not ARCHIVED.")))))

(defn- generate-msg
  "Returns the final message for a given repo"
  [messages]
  (if (pos? (count messages))
    (str message-header
         (str/join "\n" messages)
         "\n\n"
         message-footer)))

(defn- replace-vars
  "Replaces vars with values in a given string"
  [s vars]
  (if-let [var-item (first vars)]
    (let [var (seq var-item)
          name (name (first var))
          value (str (second var))]
      (replace-vars (str/replace s (str "{{" name "}}") value) (rest vars)))
    s))

(defn- generate-msg-item
  "Returns a validation message item"
  [messages vars item]
  (let [key (first item)
        msgs (wlk/stringify-keys messages)
        raw-msg (get msgs key)]
  (if (nil? raw-msg)
    (println "ERROR! Could not find key" key "in the validation messages dictionary defined in github_report.clj")
    (str "- [ ] `" key "` - " (replace-vars raw-msg vars)))))

(defn- add-msgs
  "Attaches a list of messages for each validation item"
  [repo-validation]
  (let [validations (wlk/stringify-keys (:validations repo-validation))
        filtered    (apply dissoc validations (:ignore repo-validation))
        fixes       (filter #(second %) (seq filtered))
        vars        (:vars repo-validation)
        msgs        (map #(generate-msg-item messages vars %) fixes)]
    (assoc repo-validation :message (generate-msg msgs))))

(defn- check-org
  "Returns validation for a given org"
  [org]
  (let [url       (str "https://github.com/" org)
        ; Useful for debugging purposes
        ; repo-urls [(first (gh/repos-urls url))]]
        repo-urls (gh/repos-urls url)]
    (flatten (map #(add-msgs (check-repo org %)) repo-urls))))

(defn check-repos
  "Returns validation for all public repos across all FINOS orgs"
  []
  (let [orgs finos-github-orgs
        validated-repos (mapcat #(check-org %) orgs)]
    (with-open [wrtr (io/writer "finos-repo-validation.json")]
      (.write wrtr (json/write-str validated-repos)))))
