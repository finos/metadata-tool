[#ftl output_format="JSON"]
{
  "Wiki" : {
    "confluence" : [ "https://symphonyoss.atlassian.net/wiki/" ],
    "meta"       : { "title" : "Wiki"}
  },
  "Dev List" : {
    "mbox"       : [ "dev /home/bitergia/mboxes/barnowl_symphony_dev" ],
    "meta"       : { "title" : "Dev List"}
  },
  "Foundation" : {
    "confluence" : [ "FM" ],
    "meta"       : { "title" : "Foundation"}
  }[#if activities?? && activities?size > 0],
  [#list activities as activity]
  "${activity.activity_name}" : {[#if activity.repositories?? && activity.repositories?size > 0]
    "git"        : [
      [#list activity.repositories as repository]
      "${repository.github_url}.git"[#if repository.repository_id != activity.repositories?last.repository_id],[/#if]
      [/#list]
    ],
    "github"     : [
      [#list activity.repositories as repository]
      "${repository.github_url}"[#if repository.repository_id != activity.repositories?last.repository_id],[/#if]
      [/#list]
    ],[/#if][#if activity.confluence_space_key??]
    "confluence" : [ "${activity.confluence_space_key}" ],[/#if][#if activity.mailing_list_address??]
    "mbox"       : [ [#assign ml_name=activity.mailing_list_address?split("@")?first]"${ml_name} /home/bitergia/mboxes/barnowl_symphony_${ml_name}" ],[/#if]
    "meta"       : { "title" : "${activity.activity_name}" }
  }[#if activity.activity_id != activities?last.activity_id],[/#if]
  [/#list][/#if]
}
