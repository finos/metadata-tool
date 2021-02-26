[#ftl]
[#if organizations?? && organizations?size > 0]
[
  [#list organizations as organization]
    [#if organization.cla_manager??]
  {
    "name": "${organization.organization_name}",
    "cla_manager": "${organization.cla_manager}",
    "ccla_contract": "${organization.ccla_contract}",
    "domains": [
      [#list organization.domains as domain]"${domain}"[#if domain_has_next],[/#if][/#list]
    ]
  }[#if organization_has_next],[/#if]
    [/#if]
  [/#list]
]
[/#if]