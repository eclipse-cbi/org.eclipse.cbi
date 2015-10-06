set -o nounset
set -o errexit
set -o errtrace
set -o functrace

shopt -s nullglob
shopt -s extglob
shopt -s expand_aliases

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 1.8)}"
export M2_HOME="${M2_HOME:-/Users/mbarbero/bin/apache-maven-latest}"

export PATH=${JAVA_HOME}/bin:${M2_HOME}/bin:${PATH}

WORKING_DIRECTORY="${WORKING_DIRECTORY:-$(pwd)/$(dirname "${0}")}"
pushd "${WORKING_DIRECTORY}" > /dev/null && export WORKING_DIRECTORY="$(pwd)" && popd > /dev/null

export TARGET_FOLDER="${TARGET_FOLDER:-${WORKING_DIRECTORY}/target}"
mkdir -p "${TARGET_FOLDER}"

echo "== Creating test-specific maven settings in ${TARGET_FOLDER}/settings.xml"
sed "s#LOCAL_IT_REPO#${TARGET_FOLDER}/m2#" < "${WORKING_DIRECTORY}/settings-template.xml" > "${TARGET_FOLDER}/settings.xml"
