#!/bin/bash

source "$(dirname "${0}")/init.sh"

echo "== Deploying parent =="
mvn -B -ff -U clean install \
  -s "${TARGET_FOLDER}/settings.xml" \
  -f "${WORKING_DIRECTORY}/../pom.xml"

echo "== Deploying cbi-checkstyle =="
mvn -B -ff -U clean install \
  -s "${TARGET_FOLDER}/settings.xml" \
  -f "${WORKING_DIRECTORY}/../checkstyle/pom.xml"

echo "== Building cbi-common =="
mvn -B -ff -U clean install \
  -s "${TARGET_FOLDER}/settings.xml" \
  -f "${WORKING_DIRECTORY}/../common/pom.xml"

echo "== Building cbi-maven-plugins =="
mvn -B -ff -U clean install \
  -s "${TARGET_FOLDER}/settings.xml" \
  -f "${WORKING_DIRECTORY}/../maven-plugins/pom.xml"

echo "== Building cbi-webservices =="
mvn -B -ff -U clean install \
  -s "${TARGET_FOLDER}/settings.xml" \
  -f "${WORKING_DIRECTORY}/../webservice/pom.xml"
