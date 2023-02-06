#!/bin/bash

###############################################################################
# Copyright (c) 2016 Eclipse Foundation and others
# This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Mikaël Barbero - initial implementation
###############################################################################

set -o errexit
set -o nounset
set -o pipefail

set -o errtrace
set -o functrace

shopt -s extglob
shopt -s globstar
shopt -s nullglob

SCRIPT_FOLDER="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

export RELEASE_VERSION="${2:-}"
export NEXT_DEVELOPMENT_VERSION="${3:-}"
export POM="${4:-pom.xml}"

export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m -Xms256m -XshowSettings:vm -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn}"
export GIT_BRANCH="${GIT_BRANCH:-main}"
export DRY_RUN="${DRY_RUN:-false}"

latest_maven_release() {
  local groupId=${1}
  local artifactId=${2}
  curl -sSL "https://repo1.maven.org/maven2/${groupId//\.//}/${artifactId}/maven-metadata.xml" | xml sel -t -v "metadata/versioning/release"
}

latest_maven_release_gav() {
  local groupId=${1}
  local artifactId=${2}
  echo "${groupId}:${artifactId}:$(latest_maven_release "${groupId}" "${artifactId}")"
}

export VERSIONS_MAVEN_PLUGIN="${VERSIONS_MAVEN_PLUGIN:-$(latest_maven_release_gav "org.codehaus.mojo" "versions-maven-plugin")}"
export MAVEN_HELP_PLUGIN="${MAVEN_HELP_PLUGIN:-$(latest_maven_release_gav "org.apache.maven.plugins" "maven-help-plugin")}"
export MAVEN_DEPENDENCY_PLUGIN="${MAVEN_DEPENDENCY_PLUGIN:-$(latest_maven_release_gav "org.apache.maven.plugins" "maven-dependency-plugin")}"

export ARTIFACT_ID
ARTIFACT_ID=$(xml sel -N mvn="http://maven.apache.org/POM/4.0.0" -t -v  "/mvn:project/mvn:artifactId" "${POM}")
export GROUP_ID
GROUP_ID=$(xml sel -N mvn="http://maven.apache.org/POM/4.0.0" -t -v  "/mvn:project/mvn:groupId" "${POM}")

function git-clean-reset {
  git clean -q -x -d -ff
  git checkout -q -f "${GIT_BRANCH}"
  git reset -q --hard "origin/${GIT_BRANCH}"
}

# set the version the the to-be released version and commit all changes made to the pom
prepare_release() {
  if [ "${DRY_RUN}" = true ]; then
    >&2 echo "DRY RUN: ${SCRIPT_FOLDER}/mvnw \"${VERSIONS_MAVEN_PLUGIN}:set\" -DnewVersion=\"${RELEASE_VERSION}\" -DgenerateBackupPoms=false -f \"${POM}\""
    >&2 echo "DRY RUN: git config --global user.email \"cbi-bot@eclipse.org\""
    >&2 echo "DRY RUN: git config --global user.name \"CBI Bot\""
    >&2 echo "DRY RUN: git add --all"
    >&2 echo "DRY RUN: git commit -m \"Prepare release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}\""
    >&2 echo "DRY RUN: git tag \"${GROUP_ID}_${ARTIFACT_ID}_${RELEASE_VERSION}\" -m \"Release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}\""
  else
    "${SCRIPT_FOLDER}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:set" -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false -f "${POM}"
    git config --global user.email "cbi-bot@eclipse.org"
    git config --global user.name "CBI Bot"
    git add --all
    git commit -m "Prepare release ${RELEASE_VERSION}"
    git tag "v${RELEASE_VERSION}" -m "Release ${RELEASE_VERSION}"
  fi
}

check_snapshot_deps() {
  # quick check that we don't depend on SNAPSHOT anymore
  if "${SCRIPT_FOLDER}/mvnw" "${MAVEN_DEPENDENCY_PLUGIN}:list" -f "${POM}" | grep SNAPSHOT; then
    >&2 echo "ERROR: At least one dependency to a 'SNAPSHOT' version has been found from '${POM}'"
    >&2 echo "ERROR: It is forbidden for releasing"
    exit 1
  fi

  if grep SNAPSHOT "${POM}"; then
    >&2 echo "ERROR: At least one 'SNAPSHOT' string has been found in '${POM}'"
    >&2 echo "ERROR: It is forbidden for releasing"
    exit 1
  fi
}

show_dep_updates() {
  "${SCRIPT_FOLDER}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:display-plugin-updates" -f "${POM}"
  "${SCRIPT_FOLDER}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:display-dependency-updates" -f "${POM}"
}

# build artifacts to be deployed
build() {
  if [ "${DRY_RUN}" = true ]; then
    >&2 echo "DRY RUN: ${SCRIPT_FOLDER}/mvnw clean verify -f \"${POM}\""
  else
    "${SCRIPT_FOLDER}/mvnw" clean verify -f "${POM}"
  fi
}

# Push all changes made to the pom and the release tag
push_release() {
  if [ "${DRY_RUN}" = true ]; then
    >&2 echo "DRY RUN: git push origin \"v${RELEASE_VERSION}\""
  else 
    git push origin "v${RELEASE_VERSION}"
  fi
}

# deploy built artifact
deploy() {
  if [ "${DRY_RUN}" = true ]; then
    >&2 echo "DRY RUN: ${SCRIPT_FOLDER}/mvnw deploy -f \"${POM}\""
  else 
    "${SCRIPT_FOLDER}/mvnw" deploy -f "${POM}"
  fi
}

prepare_next_dev() {
  # clean and prepare for next iteration
  git-clean-reset
  "${SCRIPT_FOLDER}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:set" -DnewVersion="${NEXT_DEVELOPMENT_VERSION}" -DgenerateBackupPoms=false -f "${POM}"
  if [ "${DRY_RUN}" = true ]; then
    >&2 echo "DRY RUN: git add --all"
    >&2 echo "DRY RUN: git commit -m \"Prepare for next development iteration (${GROUP_ID}:${ARTIFACT_ID}:${NEXT_DEVELOPMENT_VERSION})\""
    >&2 echo "DRY RUN: git push origin \"${GIT_BRANCH}\""
  else
    # commit next iteration changes
    >&2 git add --all
    >&2 git commit -m "Prepare for next development iteration (${GROUP_ID}:${ARTIFACT_ID}:${NEXT_DEVELOPMENT_VERSION})"
    >&2 git push origin "${GIT_BRANCH}"
  fi
}

main() {
  echo "Prepare release"
  prepare_release
  echo
  echo "Check snapshots dependencies"
  check_snapshot_deps
  echo
  echo "Display plugin/dependency updates"
  show_dep_updates
  echo
  echo "Build"
  build
  echo
  echo "Push tag to repository"
  push_release
  echo
  echo "Deploy"
  deploy
  echo
  echo "Prepare and push next development cycle"
  prepare_next_dev
}

"${@}"

