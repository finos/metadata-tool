[#ftl output_format="HTML"]
[#macro render_type type]
  [#if type = "PROJECT"]Project[#elseif type = "WORKING_GROUP"]Working Group[/#if]
[/#macro]
[#macro render_state state]
  [#switch state]
    [#case "INCUBATING"]<a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530363/Incubating">Incubating</a>[#break]
    [#case "RELEASED"]<a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530371/Released">Released</a>[#break]
    [#case "OPERATING"]<a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/93061261/Operating">Operating</a>[#break]
    [#case "PAUSED"]<a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/93290700/Paused">Paused</a>[#break]
    [#case "ARCHIVED"]<a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived">Archived</a>[#break]
  [/#switch]
[/#macro]
[#macro render_table title description_html activities]
  <p><b>${title}</b></p>
  <p>${description_html?no_esc}</p>
  <blockquote>
    <table width="100%" border=1 cellspacing=0 cellpadding=1>
      <thead>
        <tr>
          <th>Activity</th>
          <th>Type</th>
          <th><a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530756/Project+and+Working+Group+Lifecycles">Lifecycle State</a></th>
          <th>GitHub Repositories</th>
          <th>Project Lead/Working Group Chair</th>
        </tr>
      </thead>
      <tbody>
    [#list activities as activity]
        <tr>
          <td>${activity.activity_name}</td>
          <td>[@render_type activity.type /]</td>
          <td>[@render_state activity.state /]</td>
          <td>[#if activity.github_urls?? && activity.github_urls?size > 0][#list activity.github_urls as github_url]
            <a class="github" href="${github_url}">${github_url?keep_after("github.com/")}</a>[#if github_url != activity.github_urls?last]<br/>[/#if]
          [/#list][#else]
            This [@render_type activity.type /] has no GitHub repositories.
          [/#if]</td>
          <td>[#if activity.lead_or_chair??]
            [#local has_email_address = activity.lead_or_chair.email_addresses?? &&
                                        activity.lead_or_chair.email_addresses?size > 0 &&
                                        !activity.lead_or_chair.email_addresses?first?ends_with("@users.noreply.github.com")]
            [#if has_email_address]<a href="mailto:${activity.lead_or_chair.email_addresses?first}">[/#if]
              ${activity.lead_or_chair.full_name}
            [#if has_email_address]</a>[/#if]
          [#else]
            [#if activity.type = "PROJECT"]⚠️ This project has no lead.[#elseif activity.type = "WORKING_GROUP"]⚠️ This working group has no chair.[/#if]
          [/#if]</td>
        </tr>
    [/#list]
      </tbody>
    </table>
  </blockquote>
  <hr/>
[/#macro]
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
  <style type="text/css">
    @import url('https://fonts.googleapis.com/css?family=Montserrat');
    body {
      font-family: 'Montserrat', Verdana, san-serif;
      font-size: medium;
      font-weight: 500;
    }
    h1, h2, h3 {
      font-family: 'Montserrat', Verdana, san-serif;
    }
    p {
      font-family: 'Montserrat', Verdana, san-serif;
      font-size: medium;
      font-weight: 500;
    }
    p.footnote {
      font-size: small;
      text-align: center;
    }
    b.finos {
      color: #0086bf;
    }
    a.github {
      font-family: 'Courier New', Courier, monospace;
    }
    table, th, td {
      border: 1px solid #acacac;
      border-collapse: collapse;
    }
    th, td {
      font-family: 'Montserrat', Verdana, san-serif;
      font-size: medium;
      padding: 2px;
    }
    th {
      background-color: #acacac;
    }
  </style>
</head>
<body>
  <h3>${program.program_short_name} PMC Report as at ${now}</h3>
  <p>Please see <a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/118292491/Automated+Reports">the wiki</a>
     for more information on this report, including recommended remedies.  The
     <a href="https://metrics.finos.org/app/kibana?#/dashboard/C_ESCo_projects?_g=(refreshInterval:(display:Off,pause:!f,value:0),time:(from:now-2y,mode:quick,to:now))&_a=(filters:!(),query:(query_string:(analyze_wildcard:!t,query:'cm_program:%22${program.program_short_name}%22')))"/>${program.program_short_name} Program's metrics dashboard</a>
     also provides more insight into the activity of the Program's Projects and Working Groups.</p>
  <hr/>
  <h3>${program.program_short_name} PMC Composition</h3>
  [#if pmc-list?? && pmc-list?size > 0]
    <ul>
      [#list pmc-list as pmc_person]
        <li>${pmc_person}</li>
      [/#list]
    </ul>
  [/#if]
  <hr/>
  [#if (unarchived_activities_without_leads?? && unarchived_activities_without_leads?size > 0) ||
     (inactive_activities?? && inactive_activities?size > 0) ||
     (stale_activities?? && stale_activities?size > 0) ||
     (activities_with_unactioned_prs?? && activities_with_unactioned_prs?size > 0) ||
     (activities_with_unactioned_issues?? && activities_with_unactioned_issues?size > 0) ||
     (unarchived_activities_with_non_standard_licenses?? && unarchived_activities_with_non_standard_licenses?size > 0) ||
     (archived_activities_that_arent_github_archived?? && archived_activities_that_arent_github_archived?size > 0) ||
     (activities_with_repos_without_issues_support?? && activities_with_repos_without_issues_support?size > 0)]

  [#if unarchived_activities_without_leads?? && unarchived_activities_without_leads?size > 0]
    [@render_table "Activities Without a Lead/Chair"
                   "Here are Projects and Working Groups that are missing a lead or chair, and that are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   unarchived_activities_without_leads /]
  [/#if]

  [#if inactive_activities?? && inactive_activities?size > 0]
    [@render_table "Inactive Activities"
                   "Here are inactive Projects and Working Groups, defined as being those with no git, GitHub, Confluence, or mailing list activity in the last ${inactive_days} days, that are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   inactive_activities /]
  [/#if]

  [#if stale_activities?? && stale_activities?size > 0]
    [@render_table "Stale Activities"
                   "Here are Projects and Working Groups that are in INCUBATING state, have been contributed more than 6 months ago and are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   stale_activities /]
  [/#if]

  [#if activities_with_unactioned_prs?? && activities_with_unactioned_prs?size > 0]
    [@render_table "Activities with Unactioned PRs"
                   "Here are the Projects and Working Groups that have unactioned PRs, defined as being those with PRs with more than ${old_pr_threshold_days} days of inactivity, that are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   activities_with_unactioned_prs /]
  [/#if]

  [#if activities_with_unactioned_issues?? && activities_with_unactioned_issues?size > 0]
    [@render_table "Activities with Unactioned Issues"
                   "Here are the Projects and Working Groups that have unactioned issues, defined as being those with issues with more than ${old_issue_threshold_days} days of inactivity, that are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   activities_with_unactioned_issues /]
  [/#if]

  [#if unarchived_activities_with_non_standard_licenses?? && unarchived_activities_with_non_standard_licenses?size > 0]
    [@render_table "Activities with GitHub Repositories with Non-standard Licenses"
                   "Here are the Projects and Working Groups that have GitHub repositories that are neither <a href='https://www.apache.org/licenses/LICENSE-2.0'>Apache 2.0</a> nor <a href='https://creativecommons.org/licenses/by/4.0/'>Creative Commons Attribution 4.0 International</a> licensed according to GitHub, and that are not in <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived state</a>:"
                   unarchived_activities_with_non_standard_licenses /]
  [/#if]

  [#if archived_activities_that_arent_github_archived?? && archived_activities_that_arent_github_archived?size > 0]
    [@render_table "Archived Activities that Aren't Archived in GitHub"
                   "Here are the <a href='https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/75530367/Archived'>Archived</a> Projects and Working Groups that have GitHub repositories that haven't been archived (set to read-only) in GitHub yet:"
                   archived_activities_that_arent_github_archived /]
  [/#if]

  [#if activities_with_repos_without_issues_support?? && activities_with_repos_without_issues_support?size > 0]
    [@render_table "Activities that Have GitHub Repositories Without Issue Tracking Enabled"
                   "Here are the Projects and Working Groups that have GitHub repositories without issue tracking enabled:"
                   activities_with_repos_without_issues_support /]
  [/#if]
[#else]
  <p>No action is required - none of the ${program.program_short_name} Program's Projects and/or Working Groups have any of the issues covered by this report (and described in more detail on <a href="https://finosfoundation.atlassian.net/wiki/spaces/FINOS/pages/118292491/Automated+Reports">the wiki</a>).&nbsp;&nbsp;🎉</p>
  <hr/>
[/#if]
  <p class="footnote">Need help? Raise a <a href="https://finosfoundation.atlassian.net/secure/CreateIssue.jspa?pid=10000&issuetype=10001">HELP issue</a>
    or send an email to <a href="mailto:help@finos.org">help@finos.org</a>.<br/>
    Have an idea for improving this report? Raise <a href="https://github.com/finos/metadata-tool/issues">an enhancement request</a>.
    <br/>&nbsp;<br/>
    Copyright 2018 <b class="finos">Fintech Open Source Foundation</b><br/>
    Content in this email is licensed under the <a href="https://creativecommons.org/licenses/by/4.0/">CC BY 4.0 license</a>.<br/>
    Code in this email is licensed under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 license</a>.</p>
</body>
</html>
