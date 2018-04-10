[#ftl output_format="HTML"]
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
</head>
<body>
  <p><b>Inactive Projects Report, as at ${now}.</b></p>
  <p>Here are the currently inactive projects (defined as being those projects
     with no git commit or GitHub issue/PR activity in the last ${inactive_days}
     days), that are not in
     <a href="https://symphonyoss.atlassian.net/wiki/display/FM/Archived">Archived state</a>:</p>
  <p>
    <blockquote>
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
        <tr>
           <td>${inactive_project.activity_name}</td>
           <td>${inactive_project.state}</td>
           <td>[#list inactive_project.github-urls as github_url]
             <a href="${github_url}">${github_url}</a>[#if github_url != inactive_project.github-urls?last]<br/>[/#if]
           [/#list]</td>
           <td>[#if inactive_project.project_leads?? && inactive_project.projects_leads?size > 0][#list inactive_project.project_leads as project_lead]
             [#if project_lead.email_addresses?? &&
                  project_lead.email_addresses?size > 0 &&
                  !project_lead.email_addresses?first?ends_with("@users.noreply.github.com")]<a href="mailto:${project_lead.email_addresses?first}">[/#if]
               ${project_lead.full_name}[#if project_lead != inactive_project.project_leads?last]<br/>[/#if]
             [#if project_lead.email_addresses?? &&
                  project_lead.email_addresses?size > 0 &&
                  !project_lead.email_addresses?first?ends_with("@users.noreply.github.com")]</a>[/#if]
           [/#list][#else]
             This project has no project leads.
           [/#if]</td>
        </tr>
[/#list]
       ]
     ]
   ]
 ]
  <p>To dig further into this data, please use the <a href="https://metrics.symphony.foundation"/>metrics dashboard</a>.</p>
</body>
</html>