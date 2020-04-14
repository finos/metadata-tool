[#ftl output_format="JSON"]
[#macro bitergia_address mailing_list_address]
  [#compress]
    [#assign ml_name=mailing_list_address?split("@")?first]"${ml_name} /home/bitergia/mboxes/barnowl_symphony_${ml_name}"
  [/#compress]
[/#macro]
{
  "unknown" : {
    "confluence"    : [ "https://finosfoundation.atlassian.net/wiki/" ],
    "jira"          : [ "http://finosfoundation.atlassian.net"],
    "finosmeetings" : [ "file:///home/bitergia/conf/finos-meetings.csv",
                        "file:///home/bitergia/conf/github-finos-meetings.csv" ]
  },
  "Foundation" : {
    "meta"       : { "title" : "Foundation" },
    "mbox"       : [ "announce /home/bitergia/mboxes/barnowl_symphony_announce" ]
  }[#if programs?? && programs?size > 0],[#list programs as program]
  "${program.program_short_name} PMC" : {
    "meta"       : { "title"   : "${program.program_short_name} PMC",
    "type"       : "PMC",
    "program" : "${program.program_short_name}" },
    [#if program.pmc_confluence_page??]
      "confluence" : [ "https://finosfoundation.atlassian.net/wiki/ --filter-raw=data.ancestors._links.webui:${program.pmc_confluence_page}"],
    [/#if]
    "mbox"       : [ [#if program.program_mailing_list_address??][@bitergia_address mailing_list_address=program.program_mailing_list_address /][/#if][#if program.program_mailing_list_address?? && program.pmc_mailing_list_address??],
                     [@bitergia_address mailing_list_address=program.pmc_mailing_list_address /][/#if] ]
    [#if program.pmc_repos?? && program.pmc_repos?size > 0]
      ,"git"        : [
        [#list program.pmc_repos as repo]
          "https://github.com/${program.github_org}/${repo}.git"[#if repo != program.pmc_repos?last],[/#if]
        [/#list]
      ],
      "github"     : [
        [#list program.pmc_repos as repo]
          "https://github.com/${program.github_org}/${repo}"[#if repo != program.pmc_repos?last],[/#if]
        [/#list]
      ],
      "github:prs"     : [
        [#list program.pmc_repos as repo]
          "https://github.com/${program.github_org}/${repo}"[#if repo != program.pmc_repos?last],[/#if]
        [/#list]
      ]
    [/#if]
  }[#if program.program_id != programs?last.program_id],[/#if][/#list][/#if]
  [#if activities?? && activities?size > 0],[#list activities as activity]
  "${activity.activity_name}" : {
    "meta"       : { "title"   : "${activity.activity_name}",
                     "program" : "${activity.program_short_name}",
                     "contributed" : "${activity.contribution_date}",
                     "state"   : "${activity.state}",
                     "type"    : "${activity.type}" }[#if activity.github_urls?? && activity.github_urls?size > 0],
    [#if activity.confluence_page??]
    "confluence" : [ "https://finosfoundation.atlassian.net/wiki/ --filter-raw=data.ancestors._links.webui:${activity.confluence_page}"],
    [/#if]
    "git"        : [
      [#list activity.github_urls as github_url]
                     "${github_url}.git"[#if github_url != activity.github_urls?last],[/#if]
      [/#list]
                   ],
    "github"     : [
      [#list activity.github_urls as github_url]
                     "${github_url}"[#if github_url != activity.github_urls?last],[/#if]
      [/#list]],
    "github:prs"     : [
      [#list activity.github_urls as github_url]
                     "${github_url}"[#if github_url != activity.github_urls?last],[/#if]
      [/#list]
                   ][/#if][#if activity.confluence_space_keys?? && activity.confluence_space_keys?size > 0],
    "confluence" : [
      [#list activity.confluence_space_keys as confluence_space_key]
                     "${confluence_space_key}"[#if confluence_space_key != activity.confluence_space_keys?last],[/#if]
      [/#list]
                   ][/#if][#if activity.mailing_list_addresses?? && activity.mailing_list_addresses?size > 0],
    "mbox"       : [
      [#list activity.mailing_list_addresses as mailing_list_address]
                     [@bitergia_address mailing_list_address=mailing_list_address /][#if mailing_list_address != activity.mailing_list_addresses?last],[/#if]
      [/#list]
                   ][/#if]
  }[#if activity.activity_id != activities?last.activity_id],[/#if][/#list][/#if]
}
