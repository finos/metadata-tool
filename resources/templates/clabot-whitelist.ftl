[#ftl output_format="JSON"]
[
[#if email_domains?? && email_domains?size > 0]
  [#list email_domains as email_domain]
  "@${email_domain}",
  [/#list]
[/#if]
[#if github_ids?? && github_ids?size > 0]
  [#list github_ids as github_id]
  "${github_id}"[#if github_id != github_ids?last],[/#if]
  [/#list]
[/#if]
]
