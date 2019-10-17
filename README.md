[![Build Status](https://travis-ci.com/finos/metadata-tool.svg?token=pqqpLyKQyKTy9sWFPywW&branch=master)](https://travis-ci.com/finos/metadata-tool)
[![Open Issues](https://img.shields.io/github/issues/finos/metadata-tool.svg)](https://github.com/finos/metadata-tool/issues)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/finos/metadata-tool.svg)](http://isitmaintained.com/project/finos/metadata-tool "Average time to resolve an issue")
[![Dependencies Status](https://versions.deps.co/finos/metadata-tool/status.svg)](https://versions.deps.co/finos/metadata-tool)
[![License](https://img.shields.io/github/license/finos/metadata-tool.svg)](https://github.com/finos/metadata-tool/blob/master/LICENSE)

<img align="right" width="40%" src="https://www.finos.org/hubfs/FINOS/finos-logo/FINOS_Icon_Wordmark_Name_RGB_horizontal.png">

# metadata tool

A command line tool for performing various tasks with [Fintech Open Source Foundation (FINOS)](https://www.finos.org/) metadata.

## Installation

For now the metadata tool is available in source form only, so fire up your favourite git client and get cloning!

## Configuration

metadata tool is configured via a single [EDN](https://github.com/edn-format/edn) file that's specified on the command
line.  This configuration file contains credentials for GitHub, Bitergia, and the tool's email account (used for sending
email reports).

The following environment variables must be set, before invoking a metadata-tool command:
- `GITHUB_USER` - Your GitHub username
- `GITHUB_PASSWORD` - A GitHub personal access token; you can create it on https://github.com/settings/tokens
- `BITERGIA_USER` - Username for metrics.finos.org ; can be set to `''` for all commands except `check` and `check-remote`
- `BITERGIA_PASSWORD` - Passwotd for metrics.finos.org ; can be set to `''` for all commands except `check` and `check-remote`

See [the default `config.edn` file](https://github.com/finos/metadata-tool/blob/master/resources/config.edn) for details.

This file is loaded using the [aero](https://github.com/juxt/aero) library, which offers quite a bit
of flexibility around how values are specified in the file (they can be read from environment variables,
for example).  See the [aero documentation](https://github.com/juxt/aero/blob/master/README.md) for details.

### Logging Configuration

metadata tool uses the [logback](https://logback.qos.ch/) library for logging, and ships with a
[reasonable default `logback.xml` file](https://github.com/finos/metadata-tool/blob/master/resources/logback.xml).
Please review the [logback documentation](https://logback.qos.ch/manual/configuration.html#configFileProperty) if you
wish to override this default logging configuration.

## Usage

For now, the metadata tool should be run via Leiningen:

```
$ lein run -- -h
Runs one or more metadata tools.

Usage: metadata-tool [options] tool [tool] ...

Options:
  -c, --config-file FILE              Path of configuration file (optional, defaults to 'config.edn' in the classpath)
  -m, --metadata-directory DIRECTORY  Path of local metadata directory (optional, metadata will be checked out from GitHub if not specified)
  -r, --github-revision REVISION      GitHub revision of the metadata repository to checkout and use (optional, defaults to latest)
      --email-override                Overrides the default email behaviour of using a test email address for all outbound emails (DO NOT USE UNLESS YOU REALLY KNOW WHAT YOU'RE DOING!).
  -h, --help

Available tools:
<list of currently available tools is displayed here - this list will change as new tools are developed>
```

## Developer Information

[GitHub project](https://github.com/finos/metadata-tool)

[Bug Tracker](https://github.com/finos/metadata-tool/issues)

### Branching Structure

This project has two permanent branches called `master` and `dev`.  `master` is a
[GitHub protected branch](https://help.github.com/articles/about-protected-branches/) and cannot be pushed to directly -
all pushes (from project team members) and pull requests (from the wider community) must be made against the `dev`
branch.  The project team will periodically merge outstanding changes from `dev` to `master`.

## Contributing

1. Fork it (<https://github.com/yourname/yourproject/fork>)
2. Create your feature branch (`git checkout -b feature/fooBar`)
3. Read our [contribution guidelines](.github/CONTRIBUTING.md) and [Community Code of Conduct](https://www.finos.org/code-of-conduct)
4. Commit your changes (`git commit -am 'Add some fooBar'`)
5. Push to the branch (`git push origin feature/fooBar`)
6. Create a new Pull Request

_NOTE:_ Commits and pull requests to FINOS repositories will only be accepted from those contributors with an active, executed Individual Contributor License Agreement (ICLA) with FINOS OR who are covered under an existing and active Corporate Contribution License Agreement (CCLA) executed with FINOS. Commits from individuals not covered under an ICLA or CCLA will be flagged and blocked by the FINOS Clabot tool. Please note that some CCLAs require individuals/employees to be explicitly named on the CCLA.

*Need an ICLA? Unsure if you are covered under an existing CCLA? Email [help@finos.org](mailto:help@finos.org)*


## License

Copyright 2017 Fintech Open Source Foundation

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)

### 3rd Party Licenses

To see the full list of licenses of all third party libraries used by this project, please run:

```shell
$ lein licenses :csv | cut -d , -f3 | sort | uniq
```

To see the dependencies and licenses in detail, run:

```shell
$ lein licenses
```

TODO - document chrome driver install - https://chromedriver.storage.googleapis.com/index.html?path=75.0.3770.140/