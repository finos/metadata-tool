
# metadata tool

[![Build Status](https://circleci.com/gh/finos/metadata-tool.png?circle-token=:circle-token)](https://circleci.com/gh/finos/metadata-tool)

An easily extensible command line tool for performing various tasks with FINOS Foundation metadata.

## Installation

For now the metadata tool is available in source form only, so fire up your favourite git client and get cloning!

## Configuration

metadata tool is configured via a single [EDN](https://github.com/edn-format/edn) file that's specified on the command
line.  This configuration file contains credentials for GitHub and JIRA.

See [the sample `config.edn` file](https://github.com/finos/metadata-tool/blob/master/resources/config.edn) for details.

This file is loaded using the [aero](https://github.com/juxt/aero) library, which offers quite a bit
of flexibility around how values are specified in the file (they can be read from environment variables,
for example).  See the [aero documentation](https://github.com/juxt/aero/blob/master/README.md) for details.

## Usage

For now, the metadata tool should be run via Leiningen:

```
$ lein run -- -h
Runs one or more metadata tools.

Usage: metadata-tool [options] tool [tool] ...

Options:
  -c, --config-file FILE                                                      Path to configuration file (defaults to 'config.edn' in the classpath)
  -t, --temp-directory DIR  <default value of java.io.tmpdir on your system>  Temporary directory in which to checkout metadata (defaults to value of java.io.tmpdir property)
  -h, --help

Available tools:
<list of currently available tools is displayed here - this list will change as new tools are developed>
$ lein run -- -c <path to EDN configuration file> tool [tool] ...
$ lein run -- -c <path to EDN configuration file> -t <path of desired temp directory> tool [tool] ...
```

## Developer Information

[GitHub project](https://github.com/finos/metadata-tool)

[Bug Tracker](https://github.com/finos/metadata-tool/issues)

## License

Copyright © 2017 FINOS Foundation

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
