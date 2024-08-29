#!/usr/bin/env bash

#*******************************************************************************
# Copyright (c) 2023 Eclipse Foundation and others.
# This program and the accompanying materials are made available
# under the terms of the Eclipse Public License 2.0
# which is available at http://www.eclipse.org/legal/epl-v20.html
# SPDX-License-Identifier: EPL-2.0
#*******************************************************************************

# Bash strict-mode
set -o errexit
set -o nounset
set -o pipefail

IFS=$'\n\t'

VERSION="${1:-}"

if [[ -z "${VERSION}" ]]; then
  >&2 printf "ERROR: a version must be given.\n"
  exit 1
fi

git checkout "tags/v${VERSION}" -b "v${VERSION}-branch"

./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet "${VERSION}"
./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet "${VERSION}"
./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet "${VERSION}"
./webservice/service-deployment.sh webservice/signing/macosx/service.jsonnet "${VERSION}"

git checkout main
git branch -d "v${VERSION}-branch"