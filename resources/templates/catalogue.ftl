[#ftl output_format="JSON"]
{
  "activities" : [
[#if activities?? && activities?size > 0]
  [#list activities as activity]
    {
      "programName"          : "${activity.program_name}",
      "activityName"         : "${activity.activity_name}",
      "type"                 : "${activity.type}",
      "state"                : "${activity.state}",
      "contributionDate"     : "${activity.contribution_date}"[#if activity.release_date??],
      "releaseDate"          : "${activity.release_date}"[/#if][#if activity.archive_date??],
      "archiveDate"          : "${activity.archive_date}"[/#if][#if activity.tags?? && activity.tags?size > 0],
      "tags"                 : [
      [#list activity.tags as tag]
        "${tag}"[#if tag != activity.tags?last],[/#if]
      [/#list]
      ][/#if][#if activity.github_repos?? && activity.github_repos?size > 0],
      "gitHubRepos"          : [
      [#list activity.github_repos as github_repo]
        {
          "name"          : "${github_repo.name!}",
          "description"   : "${github_repo.description!}",
          "gitHubURL"     : "${github_repo.url}",
          "hotness"       : ${(github_repo.hotness!0)?c},
          "watchers"      : ${(github_repo.watchers!0)?c},
          "size"          : ${(github_repo.size!0)?c},
          "collaborators" : ${(github_repo.collaborators!0)?c},
          "stars"         : ${(github_repo.stars!0)?c},
          "forks"         : ${(github_repo.forks!0)?c},
          "openIssues"    : ${(github_repo.open_issues!0)?c}[#if github_repo.languages??],
          "languages"     : {
          [#list github_repo.languages?keys as language_name]
            "${language_name}" : ${github_repo.languages[language_name]?c}[#if language_name != github_repo.languages?keys?last],[/#if]
          [/#list]
          }[/#if]
        }[#if github_repo.name != activity.github_repos?last.name],[/#if]
      [/#list]
      ][/#if][#if activity.cumulative_github_stats??],
      "cumulativeGitHubStats" : {
        "hotness"       : ${(activity.cumulative_github_stats.hotness!0)?c},
        "watchers"      : ${(activity.cumulative_github_stats.watchers!0)?c},
        "size"          : ${(activity.cumulative_github_stats.size!0)?c},
        "collaborators" : ${(activity.cumulative_github_stats.collaborators!0)?c},
        "stars"         : ${(activity.cumulative_github_stats.stars!0)?c},
        "forks"         : ${(activity.cumulative_github_stats.forks!0)?c},
        "openIssues"    : ${(activity.cumulative_github_stats.open_issues!0)?c}[#if activity.cumulative_github_stats.languages?? && activity.cumulative_github_stats.languages?size > 0],
        "languages"     : {
        [#list activity.cumulative_github_stats.languages?keys as language_name]
          "${language_name}" : ${(activity.cumulative_github_stats.languages[language_name]!0)?c}[#if language_name != activity.cumulative_github_stats.languages?keys?last],[/#if]
        [/#list]
        }[/#if]
      }[/#if][#if activity.mailing_lists?? && activity.mailing_lists?size > 0],
      "mailingLists"       : [
      [#list activity.mailing_lists as mailing_list]
        {
          "emailAddress"  : "${mailing_list.email_address}",
          "webArchiveUrl" : "${mailing_list.web_archive_url}"
        }[#if mailing_list.email_address != activity.mailing_lists?last.email_address],[/#if]
      [/#list]
      ][/#if][#if activity.confluence_spaces?? && activity.confluence_spaces?size > 0],
      "confluenceSpaces"   : [
      [#list activity.confluence_spaces as confluence_space]
        {
          "key" : "${confluence_space.key}",
          "url" : "${confluence_space.url}"
        }[#if confluence_space.key != activity.confluence_spaces?last.key],[/#if]
      [/#list]
      ][/#if]
    }[#if activity.activity_name != activities?last.activity_name],[/#if]
  [/#list]
[/#if]
  ],
  "allTags" : [
[#if all_tags?? && all_tags?size > 0]
  [#list all_tags as tag]
    "${tag}"[#if tag != all_tags?last],[/#if]
  [/#list]
[/#if]
  ]
}
