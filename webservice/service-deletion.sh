#! /usr/bin/env bash
#*******************************************************************************
# Copyright (c) 2020 Eclipse Foundation and others.
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
SCRIPT_FOLDER="$(readlink -f "$(dirname "${BASH_SOURCE[0]}")")"

SERVICE_JSON_FILE=${1}
SERVICE_PATH="$(realpath --relative-to="${SCRIPT_FOLDER}/.." "$(readlink -f "$(dirname "${SERVICE_JSON_FILE}")")")"

if [[ "$(kubectl config current-context)" != okd* ]]; then
  echo "ERROR: bad context: not deploying to okd cluster"
  echo "Current context = $(kubectl config current-context)"
  exit 1
fi

LOCAL_CONFIG="${HOME}/.cbi/config"

if [[ ! -f "${LOCAL_CONFIG}" ]] && [[ -z "${PASSWORD_STORE_DIR:-}" ]]; then
  echo "ERROR: File '$(readlink -f "${LOCAL_CONFIG}")' does not exists"
  echo "Create one to configure the location of the password store. Example:"
  echo '{"password-store": {"it-dir": "~/.password-store"}}' | jq '.'
fi

# Retrieve the version of the project from the pom.xml file
echo
echo "Retrieve version of the project from the pom.xml file..."
# shellcheck disable=SC2030
PROJECT_VERSION="${2:-"$(export MAVEN_SKIP_RC=true && "${SCRIPT_FOLDER}/../mvnw" -f "${SCRIPT_FOLDER}/../pom.xml" help:evaluate -Dexpression=project.version -q -DforceStdout -pl "${SERVICE_PATH}")"}"
# shellcheck disable=SC2031
ARTIFACT_ID="$(export MAVEN_SKIP_RC=true && "${SCRIPT_FOLDER}/../mvnw" -f "${SCRIPT_FOLDER}/../pom.xml" help:evaluate -Dexpression=project.artifactId -q -DforceStdout -pl "${SERVICE_PATH}")"

# Generate the json from the jsonnet files
echo
echo "Generate json from jsonnet..."
SERVICE_JSON=$(jsonnet \
  --ext-str version="${PROJECT_VERSION}" \
  --ext-str artifactId="${ARTIFACT_ID}" \
  "${SERVICE_JSON_FILE}")

# Pause deployment
DEPLOYMENT_NAME="$(jq -r '.kube.resources[] | select (.kind == "Deployment").metadata.name' <<<"${SERVICE_JSON}")"
DEPLOYMENT_NAMESPACE="$(jq -r '.kube.resources[] | select (.kind == "Deployment").metadata.namespace' <<<"${SERVICE_JSON}")"
kubectl rollout pause deployment -n "${DEPLOYMENT_NAMESPACE}" "${DEPLOYMENT_NAME}"

# Delete
echo
echo "Delete..."
jq -r '.["kube.yml"]' <<<"${SERVICE_JSON}" | kubectl delete -f -
