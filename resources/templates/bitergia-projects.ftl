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
  "WG-DW" : {
    "confluence" : [ "WGDWAPI" ],
    "mbox"       : [ "wg-desktop-wrapper /home/bitergia/mboxes/barnowl_symphony_wg-desktop-wrapper" ],
    "meta"       : { "title" : "WG-DW"}
  },
  "WG-API" : {
    "confluence" : [ "WGA" ],
    "mbox"       : [ "wg-api /home/bitergia/mboxes/barnowl_symphony_wg-api" ],
    "meta"       : { "title" : "WG-API"}
  },
  "WG-FOS" : {
    "confluence" : [ "WGFOS" ],
    "mbox"       : [ "wg-financial-objects /home/bitergia/mboxes/barnowl_symphony_wg-financial-objects" ],
    "meta"       : { "title" : "WG-FOS"}
  },
  "ESCo" : {
    "confluence" : [ "ESCo" ],
    "meta"       : { "title" : "ESCo"}
  },
  "Foundation" : {
    "confluence" : [ "FM" ],
    "meta"       : { "title" : "Foundation"}
  }[#if projects?? && projects?size > 0],
  [#list projects as project]
  "${project.project_name}" : {
    "git"    : [
    [#list project.repositories as repository]
      "${repository.github_url}.git"[#if repository != repositories?last],[/#if]
    [/#list]
    ],
    "github" : [
    [#list project.repositories as repository]
      "${repository.github_url}.git"[#if repository != repositories?last],[/#if]
    [/#list]
    ],
    "meta"   : { "title" : "${project.project_name}" }
  }[#if project != projects?last],[/#if]
  [/#list]
[/#if]

}
