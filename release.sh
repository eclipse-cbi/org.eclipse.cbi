#!/bin/bash

###############################################################################
# Copyright (c) 2016 Eclipse Foundation and others
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
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
#shopt -s globstar
shopt -s nullglob

# fail fast if not set
export RELEASE_VERSION="${RELEASE_VERSION}"
export DEVELOPMENT_VERSION="${DEVELOPMENT_VERSION}"
export POM="${1:-pom.xml}"

export JAVA_HOME="${JAVA_HOME:-/shared/common/jdk1.8.0_x64-latest}"
export M2_HOME="${M2_HOME:-/shared/common/apache-maven-latest}"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m -Xms256m -XX:MaxPermSize=256M}"
export GIT_BRANCH="${GIT_BRANCH:-master}"
export WORKSPACE="${WORKSPACE:-$(pwd)}"
export DRY_RUN="${DRY_RUN:+true}"

export VERSIONS_MAVEN_PLUGIN="${VERSIONS_MAVEN_PLUGIN:-org.codehaus.mojo:versions-maven-plugin:2.1}"
export MAVEN_HELP_PLUGIN="${MAVEN_HELP_PLUGIN:-org.apache.maven.plugins:maven-help-plugin:2.2}"
export MAVEN_DEPENDENCY_PLUGIN="${MAVEN_DEPENDENCY_PLUGIN:-org.apache.maven.plugins:maven-dependency-plugin:2.10}"

export ARTIFACT_ID=$(${M2_HOME}/bin/mvn -B ${MAVEN_HELP_PLUGIN}:evaluate -Dexpression=project.artifactId -f ${POM} | grep -Ev '(^\[)')
export GROUP_ID=$(${M2_HOME}/bin/mvn -B ${MAVEN_HELP_PLUGIN}:evaluate -Dexpression=project.groupId -f ${POM} | grep -Ev '(^\[)')

function mvn {
    echo "${M2_HOME}/bin/mvn -e -C -U -V -B -Dmaven.repo.local=$WORKSPACE/.maven/repo -Djava.io.tmpdir=$WORKSPACE/.maven/tmp $@"
    "${M2_HOME}/bin/mvn" -e -C -U -V -B -Dmaven.repo.local=$WORKSPACE/.maven/repo -Djava.io.tmpdir=$WORKSPACE/.maven/tmp $@
}

function git-clean-reset {
  git clean -q -x -d -ff
  git reset -q --hard "${GIT_BRANCH}"
  git checkout -q "${GIT_BRANCH}"
}

# clean and set the version the the to-be released version
git-clean-reset
mvn ${VERSIONS_MAVEN_PLUGIN}:set -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false -f "${POM}"

# quick check that we don't depend on SNAPSHOT anymore
( mvn ${MAVEN_DEPENDENCY_PLUGIN}:list -f "${POM}" | grep SNAPSHOT ) && {
  echo "At least one dependency to a 'SNAPSHOT' version has been found from '${POM}'"
  echo "It is forbidden for releasing"
  exit 1
}

grep SNAPSHOT "${POM}" && {
  echo "At least one 'SNAPSHOT' string has been found in '${POM}'"
  echo "It is forbidden for releasing"
  exit 1
}

if [ -z ${DRY_RUN} ]; then
  # commit all changes made to the pom
  git add --all
  git commit -m "Prepare release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}"
  git tag "${GROUP_ID}_${ARTIFACT_ID}_${RELEASE_VERSION}" -m "Release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}"
  git push origin "${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}"
  git push origin "${GIT_BRANCH}"
else 
  echo "DRY RUN: git add --all"
  echo "DRY RUN: git commit -m \"Prepare release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}\""
  echo "DRY RUN: git tag \"${GROUP_ID}_${ARTIFACT_ID}_${RELEASE_VERSION}\" -m \"Release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}\""
  echo "DRY RUN: git push origin \"${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}\""
  echo "DRY RUN: git push origin \"${GIT_BRANCH}\""
fi

# build artifacts to be deployed
mvn clean verify -f "${POM}"

if [ -z ${DRY_RUN} ]; then
  # deploy build artifact
  mvn deploy -f "${POM}"
else 
  echo "DRY RUN: mvn deploy -f \"${POM}\""
fi

# clean and prepare for next iteration
git-clean-reset
mvn ${VERSIONS_MAVEN_PLUGIN}:set -DnewVersion=${DEVELOPMENT_VERSION} -DgenerateBackupPoms=false -f ${POM}

if [ -z ${DRY_RUN} ]; then
  # commit next iteration changes
  git add --all
  git commit -m "Prepare for next development iteration (${GROUP_ID}:${ARTIFACT_ID}:${DEVELOPMENT_VERSION})"
  git push origin "${GIT_BRANCH}"
else
  echo "DRY RUN: git add --all"
  echo "DRY RUN: git commit -m \"Prepare for next development iteration (${GROUP_ID}:${ARTIFACT_ID}:${DEVELOPMENT_VERSION})\""
  echo "DRY RUN: git push origin \"${GIT_BRANCH}\""
fi
