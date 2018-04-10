[#ftl output_format="JSON"]
{
  "Wiki" : {
    "confluence" : [ "https://finosfoundation.atlassian.net/wiki/" ],
    "meta"       : { "title" : "Wiki" }
  },
  "Symphony Program Public Mailing List" : {[#-- ####TODO: NEED A BETTER WAY OF HANDLING PROGRAM-LEVEL MAILING LISTS!  https://github.com/chaoss/grimoirelab/issues/83 may help here --]
    "mbox"       : [ "dev /home/bitergia/mboxes/barnowl_symphony_dev" ],
    "meta"       : { "title" : "Symphony Program Public Mailing List" }
  },
  "Foundation" : {
    "confluence" : [ "FINOS" ],
    "meta"       : { "title" : "Foundation"}
  }[#if activities?? && activities?size > 0],
  [#list activities as activity]
  "${activity.activity_name}" : {[#if activity.github_urls?? && activity.github_urls?size > 0]
    "git"        : [
      [#list activity.github_urls as github_url]
                     "${github_url}.git"[#if github_url != activity.github_urls?last],[/#if]
      [/#list]
                   ],
    "github"     : [
      [#list activity.github_urls as github_url]
                     "${github_url}"[#if github_url != activity.github_urls?last],[/#if]
      [/#list]
                   ],[/#if][#if activity.confluence_space_keys?? && activity.confluence_space_keys?size > 0]
    "confluence" : [
      [#list activity.confluence_space_keys as confluence_space_key]
                     "${confluence_space_key}"[#if confluence_space_key != activity.confluence_space_keys?last],[/#if]
      [/#list]
                   ],[/#if][#if activity.mailing_list_addresses?? && activity.mailing_list_addresses?size > 0]
    "mbox"       : [
      [#list activity.mailing_list_addresses as mailing_list_address][#assign ml_name=mailing_list_address?split("@")?first]
                     "${ml_name} /home/bitergia/mboxes/barnowl_symphony_${ml_name}"[#if mailing_list_address != activity.mailing_list_addresses?last],[/#if]
      [/#list]
                   ],[/#if]
    "meta"       : { "title" : "${activity.activity_name}" }
  }[#if activity.activity_id != activities?last.activity_id],[/#if]
  [/#list][/#if]
}
