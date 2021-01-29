[#ftl]
[#if organizations?? && organizations?size > 0]
[
  [#list organizations as organization]
    [#if organization.has_ccla??]
  {
    "name": "${organization.organization_name}",
    [#if organization.cla_manager??]
    "cla_manager": "${organization.cla_manager}",
    [/#if]
    [#if organization.ccla_contract??]
    "ccla_contract": "${organization.ccla_contract}",
    [/#if]
    "domains": [
      [#list organization.domains as domain]"${domain}"[#if domain_has_next],[/#if][/#list]
    ]
  }[#if organization_has_next],[/#if]
    [/#if]
  [/#list]
]
[/#if]