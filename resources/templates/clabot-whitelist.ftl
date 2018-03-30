[#ftl output_format="JSON"]
[
[#if github_ids?? && github_ids?size > 0]
  [#list github_ids as github_id]
  "${github_id}"[#if github_id != github_ids?last],[/#if]
  [/#list]
[/#if]
]
