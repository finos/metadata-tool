[#ftl output_format="HTML"]
[#macro render_project_row name state github_urls leads]
        <tr>
           <td>${name}</td>
           <td>${state}</td>
           <td>[#if github_urls?? && github_urls?size > 0][#list github-urls as github_url]
             <a href="${github_url}">${github_url}</a>[#if github_url != github-urls?last]<br/>[/#if]
           [/#list][#else]
             This project has no GitHub repositories.
           [/#if]</td>
           <td>[#if leads?? && leads?size > 0][#list leads as lead]
             [#if lead.email_addresses?? &&
                  lead.email_addresses?size > 0 &&
                  !lead.email_addresses?first?ends_with("@users.noreply.github.com")]<a href="mailto:${lead.email_addresses?first}">[/#if]
               ${lead.full_name}[#if lead != leads?last]<br/>[/#if]
             [#if lead.email_addresses?? &&
                  lead.email_addresses?size > 0 &&
                  !lead.email_addresses?first?ends_with("@users.noreply.github.com")]</a>[/#if]
           [/#list][#else]
             This project has no leads.
           [/#if]</td>
        </tr>
[/#macro]

<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
</head>
<body>
  <p><b>PMC Report, as at ${now}.</b></p>
  <h1>Inactive Projects</h2>
  <p>Here are the currently inactive projects, defined as being those projects
     with no git commit or GitHub issue/PR activity in the last ${inactive_days}
     days), that are not in
     <a href="https://symphonyoss.atlassian.net/wiki/display/FM/Archived">Archived state</a>:</p>
  <p>
    <blockquote>
[#if inactive_projects?? && inactive_projects?size > 0]
     <table width="600px" border=1 cellspacing=0>
      <thead>
        <tr bgcolor="#CCCCCC">
          <th>Project</th>
          <th>Lifecycle State</th>
          <th>Repositories</th>
          <th>Project Leads</th>
        </tr>
      </thead>
      <tbody>
[#list inactive_projects as inactive_project]
        [@render_project_row inactive_project.activity_name
                             inactive_project.state
                             inactive_project.github-urls
                             inactive_project.project_leads /]
[/#list]
       </tbody>
     </table>
[#else]
     No projects are inactive.
[/#if]
   </blockquote>
 </p>
  <h1>Projects with Unactioned PRs</h2>
  <p>Here are the projects that have unactioned PRs, defined as being those projects with PRs
     with more than ${old_pr_days} days of inactivity, that are not in
     <a href="https://symphonyoss.atlassian.net/wiki/display/FM/Archived">Archived state</a>:</p>
  <p>
    <blockquote>
[#if projects-with-unactioned-prs?? && projects-with-unactioned-prs?size > 0]
     <table width="600px" border=1 cellspacing=0>
      <thead>
        <tr bgcolor="#CCCCCC">
          <th>Project</th>
          <th>Lifecycle State</th>
          <th>Repositories</th>
          <th>Project Leads</th>
        </tr>
      </thead>
      <tbody>
[#list projects_with_unactioned_prs as project_with_unactioned_prs]
        [@render_project_row project_with_unactioned_prs.activity_name
                             project_with_unactioned_prs.state
                             inactive_project.github-urls
                             inactive_project.project_leads /]
[/#list]
       </tbody>
     </table>
[#else]
     No projects have unactioned PRs.
[/#if]
   </blockquote>
 </p>
  <p>To dig further into this data, please use the <a href="https://metrics.symphony.foundation"/>metrics dashboard</a>.</p>
</body>
</html>