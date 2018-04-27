[#ftl output_format="HTML"]
[#macro activity_table_head]
      <thead>
        <tr bgcolor="#CCCCCC">
          <th>Activity</th>
          <th>Type</th>
          <th>Lifecycle State</th>
          <th>GitHub Repositories</th>
          <th>Project Lead/Working Group Chair</th>
        </tr>
      </thead>
[/#macro]
[#macro render_activity_row name type state github_urls lead_chair]
        <tr>
           <td>${name}</td>
           <td>[#if type = "PROJECT"]Project[#elseif type = "WORKING_GROUP"]Working Group[/#if]</td>
           <td>${state}</td>
           <td>[#if github_urls?? && github_urls?size > 0][#list github_urls as github_url]
             <a href="${github_url}">${github_url}</a>[#if github_url != github_urls?last]<br/>[/#if]
           [/#list][#else]
             This project has no GitHub repositories.
           [/#if]</td>
           <td>[#if lead_or_chair??]
             [#if lead_or_chair.email_addresses?? &&
                  lead_or_chair.email_addresses?size > 0 &&
                  !lead_or_chair.email_addresses?first?ends_with("@users.noreply.github.com")]<a href="mailto:${lead_or_chair.email_addresses?first}">[/#if]
               ${lead_or_chair.full_name}
             [#if lead_or_chair.email_addresses?? &&
                  lead_or_chair.email_addresses?size > 0 &&
                  !lead_or_chair.email_addresses?first?ends_with("@users.noreply.github.com")]</a>[/#if]
           [#else]
             This project has no leads.
           [/#if]</td>
        </tr>
[/#macro]
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
</head>
<body>
  <h3>PMC Report for ${program.program_short_name}, as at ${now}</h3>
[#if (inactive_activities?? && inactive_activities?size > 0) ||
     (activities_with_unactioned_prs?? && activities_with_unactioned_prs?size > 0) ||
     (archived_activities_that_arent_github_archived?? && archived_activities_that_arent_github_archived?size > 0)]
  [#if inactive_activities?? && inactive_activities?size > 0]
  <p><b>Inactive Activities</b></p>
  <p>Here are inactive Projects and Working Groups, defined as being those
     with no git commit or GitHub issue/PR activity in the last ${inactive_days}
     days), that are not in
     <a href="#### TODO ####">Archived state</a>:</p>
  <p>
    <blockquote>
      <table width="600px" border=1 cellspacing=0>
[@activity_table_head /]
        <tbody>
    [#list inactive_activities as activity]
[@render_activity_row activity.activity_name
                      activity.type
                      activity.state
                      activity.github_urls
                      activity.lead_or_chair /]
    [/#list]
        </tbody>
      </table>
    </blockquote>
  </p>
  <p>The PMC should contact the associated Project Leads and/or Working Group Chairs to
     help determine whether these activities should be formally archived.</p>
  [/#if]
  [#if activities_with_unactioned_prs?? && activities_with_unactioned_prs?size > 0]
  <p><b>Activities with Unactioned PRs</b></p>
  <p>Here are the Projects and Working Groups that have unactioned PRs, defined as being those
     with PRs with more than ${old_pr_days} days of inactivity, that are not in
     <a href="#### TODO ####">Archived state</a>:</p>
  <p>
    <blockquote>
     <table width="600px" border=1 cellspacing=0>
[@activity_table_head /]
       <tbody>
    [#list activities_with_unactioned_prs as activity]
[@render_activity_row activity.activity_name
                      activity.type
                      activity.state
                      activity.github_urls
                      activity.lead_or_chair /]
    [/#list]
        </tbody>
      </table>
    </blockquote>
  </p>
  <p>The PMC should contact the associated Project Leads and/or Working Group Chairs to
     try to understand why PRs are remaining outstanding for so long.  If there is no
     compelling reason for these delays, the PMC should steer the Project or Working
     Group towards being more responsive to community contributions.</p>
  [/#if]
  [#if archived_activities_that_arent_github_archived?? && archived_activities_that_arent_github_archived?size > 0]
  <p><b>Archived Activities that Aren't Archived in GitHub</b></p>
  <p>Here are the <a href="#### TODO ####">Archived</a>
     Projects and Working Groups that have GitHub repositories that haven't been archived (set read-only)
     in GitHub yet:</p>
  <p>
    <blockquote>
     <table width="600px" border=1 cellspacing=0>
[@activity_table_head /]
       <tbody>
    [#list archived_activities_that_arent_github_archived as activity]
[@render_activity_row activity.activity_name
                      activity.type
                      activity.state
                      activity.github_urls
                      activity.lead_or_chair /]
    [/#list]
        </tbody>
      </table>
    </blockquote>
  </p>
  <p>The PMC Lead (or anyone to whom program-level administrative authority has been delegated)
     should <a href="#### TODO ####">archive all GitHub repositories</a> for each of these
     activities.</p>
  [/#if]
  [#if activities_with_repos_without_issues_support?? && activities_with_repos_without_issues_support?size > 0]
  <p><b>Activities that Have GitHub Repositories Without Issue Tracking Enabled</b></p>
  <p>Here are the Projects and Working Groups that have GitHub repositories that don't
     have issue tracking enabled:</p>
  <p>
    <blockquote>
     <table width="600px" border=1 cellspacing=0>
[@activity_table_head /]
       <tbody>
    [#list activities_with_repos_without_issues_support as activity]
[@render_activity_row activity.activity_name
                      activity.type
                      activity.state
                      activity.github_urls
                      activity.lead_or_chair /]
    [/#list]
        </tbody>
      </table>
    </blockquote>
  </p>
  <p>The PMC should contact the associated Project Leads and/or Working Group Chairs and
     request that public issue tracking be enabled.  All activities hosted by FINOS are
     required to have a public issue tracker writable by the community at all times.</p>
  [/#if]
  <p>&nbsp;</p>
  <p>The <a href="https://metrics.finos.org/"/>metrics dashboard</a> provides more insight
     into Project and Working Group activity.</p>
[#else]
  <p>All Projects and Working Groups are clean!  Nice work!  🎉</p>
[/#if]
</body>
</html>