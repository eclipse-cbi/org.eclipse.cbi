#!/bin/bash

##############################################################################
# Copyright (c) 2016 Eclipse Foundation and others.
# This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#    Mikaël Barbero (Eclipse Foundation)
##############################################################################

# switch to strict mode
set -o nounset
set -o errexit
set -o pipefail

shopt -s nullglob
shopt -s extglob
shopt -s expand_aliases

export SCRIPT_NAME="$(basename ${0})"
export SCRIPT_PATH="$(dirname "${0}")"
export SCRIPT_READLINK="$(readlink -e -n "${0}")"
export SCRIPT_REALNAME="$(basename "${SCRIPT_READLINK}")"
export SCRIPT_REALPATH="$(dirname "${SCRIPT_READLINK}")"

source "${SCRIPT_REALPATH}/init.sh"

if ! echo "42:user:file:now::skiprepack:java6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*File 'file' is not valid jar or zip file"; then
  fail "sign_queue_process.sh should have failed because 'file' is not a valid file to be signed"
fi

if ! echo "42:user:test-staging/notexisting.jar:now::skiprepack:java6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*File 'test-staging/notexisting.jar' is not valid jar or zip file"; then
  fail "sign_queue_process.sh should have failed because 'test-staging/notexisting.jar' is not a valid file to be signed"
fi

if ! echo "42:user:test-staging/notexisting.zip:now::skiprepack:java6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*File 'test-staging/notexisting.zip' is not valid jar or zip file"; then
  fail "sign_queue_process.sh should have failed because 'test-staging/notexisting.zip' is not a valid file to be signed"
fi

cp test-staging/hello.jar test-staging/hello.notjar
if ! echo "42:user:test-staging/hello.notjar:now::skiprepack:java6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*File 'test-staging/hello.notjar' is not valid jar or zip file"; then
  fail "sign_queue_process.sh should have failed because 'test-staging/hello.notjar' is not a valid file to be signed"
fi

if ! echo "42:user:test-staging/hello.jar:now::skiprepack:cafe6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*Java version 'cafe6' in queue file is invalid"; then
  fail "sign_queue_process.sh should have failed because 'cafe6' is not a valid java version string"
fi

if ! echo "42:user:test-staging/hello.jar:now::skiprepack:" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*Java version '' in queue file is invalid"; then
  fail "sign_queue_process.sh should have failed because '' is not a valid java version string"
fi

if ! echo "42:user:test-staging/hello.jar:now:/not/existing/folder:skiprepack:java7" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" | egrep -q "[ERROR].*Output directory '/not/existing/folder' is not a valid directory"; then
  fail "sign_queue_process.sh should have failed because '/not/existing/folder' is not a valid output directory"
fi

cp test-staging/hello.jar test-staging/hello.6.jar
if echo "42:user:test-staging/hello.6.jar:now::skiprepack:java6" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
    if ! cat "${LOGFILE}" | egrep -q "Using '.*${JDKS_java6}/bin/java' to run '${JAR_PROCESSORS_java6}'"; then
      fail "Bad JDK or Jar processor selected when using java 6"
    fi
else 
  fail "Failed with 42:user:test-staging/hello.6.jar:now::skiprepack:java6"
fi
if ! jar_is_signed test-staging/hello.6.jar; then
  fail "test-staging/hello.6.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.7.jar
if echo "42:user:test-staging/hello.7.jar:now::skiprepack:java7" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
    if ! cat "${LOGFILE}" | egrep -q "Using '.*${JDKS_java7}/bin/java' to run '${JAR_PROCESSORS_java7}'"; then
      fail "Bad JDK or Jar processor selected when using java 7"
    fi
  else 
    fail "Failed with 42:user:test-staging/hello.7.jar:now::skiprepack:java7"
fi
if ! jar_is_signed test-staging/hello.7.jar; then
  fail "test-staging/hello.7.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.8.jar
if echo "42:user:test-staging/hello.8.jar:now::skiprepack:java8" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
    if ! cat "${LOGFILE}" | egrep -q "Using '.*${JDKS_java8}/bin/java' to run '${JAR_PROCESSORS_java8}'"; then
      fail "Bad JDK or Jar processor selected when using java 8"
    fi
else 
    fail "Failed with 42:user:test-staging/hello.8.jar:now::skiprepack:java8"
fi
if ! jar_is_signed test-staging/hello.8.jar; then
  fail "test-staging/hello.8.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.skiprepack.nooutputdir.jar
if echo "42:user:test-staging/hello.skiprepack.nooutputdir.jar:now::skiprepack:java8" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
  if cat "${LOGFILE}" | egrep -q "Executing .* -repack "; then
    fail "-repack should not be added when skiprepack is specified"
  fi
  if ! cat "${LOGFILE}" | egrep -q "Executing .* -outputDir test-staging "; then
    fail "-outputDir should be the same as the input when not explicitely specified"
  fi
else 
    fail "Failed with 42:user:test-staging/hello.skiprepack.nooutputdir.jar:now::skiprepack:java8"
fi
if ! jar_is_signed test-staging/hello.skiprepack.nooutputdir.jar; then
  fail "test-staging/hello.skiprepack.nooutputdir.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.repack.outputdir.jar
if echo "42:user:test-staging/hello.repack.outputdir.jar:now:output-staging::java8" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
  if ! cat "${LOGFILE}" | egrep -q "Executing .* -repack "; then
    fail "-repack should be added when skiprepack is not specified"
  fi
  if ! cat "${LOGFILE}" | egrep -q "Executing .* -outputDir output-staging "; then
    fail "-outputDir should the the one explicitely specified"
  fi
else 
    fail "Failed with 42:user:test-staging/hello.repack.outputdir.jar:now:output-staging::java8"
fi
if ! jar_is_signed output-staging/hello.repack.outputdir.jar; then
  fail "output-staging/hello.repack.outputdir.jar should be signed"
fi

cp test-staging/hello.jar test-staging/hello.skiprepack.outputdir.jar
if echo "42:user:test-staging/hello.skiprepack.outputdir.jar:now:output-staging:skiprepack:java8" | "${SCRIPT_REALPATH}/../sign_queue_process.sh" &> "${LOGFILE}"; then
  if cat "${LOGFILE}" | egrep -q "Executing .* -repack "; then
    fail "-repack should not be added when skiprepack is specified"
  fi
  if ! cat "${LOGFILE}" | egrep -q "Executing .* -outputDir output-staging "; then
    fail "-outputDir should be the same as the input when not explicitely specified"
  fi
else 
    fail "Failed with 42:user:test-staging/hello.skiprepack.outputdir.jar:now:output-staging:skiprepack:java8"
fi
## We should test "output-staging/hello.skiprepack.outputdir.jar" but for some reasons
# jarprocessor does not use outputDir when skiprepack!!
if ! jar_is_signed test-staging/hello.skiprepack.outputdir.jar; then
  fail "test-staging/hello.skiprepack.outputdir.jar should be signed"
fi

echo -n "" > "${LOGFILE}"
rm -f "${QUEUE}"
if "${SCRIPT_REALPATH}/../sign_queue_process.sh"; then
  if cat "${LOGFILE}" | egrep -q "Processing queue item"; then
    fail "Should not have processed anything out of an empty file"
  fi
  if [[ -f "${QUEUE}" ]]; then
    fail "Queue '${QUEUE}' should have been consumed"
  fi
else 
    fail "Failed to process not existing queue"
fi

echo -n "" > "${LOGFILE}"
echo "" > "${QUEUE}"
if "${SCRIPT_REALPATH}/../sign_queue_process.sh"; then
  if cat "${LOGFILE}" | egrep -q "Processing queue item"; then
    fail "Should not have processed anything out of an empty file"
  fi
  if [[ -f "${QUEUE}" ]]; then
    fail "Queue '${QUEUE}' should have been consumed"
  fi
else 
    fail "Failed to process empty queue (newline only)"
fi

echo -n "" > "${LOGFILE}"
echo -n "" > "${QUEUE}"
if "${SCRIPT_REALPATH}/../sign_queue_process.sh"; then
  if cat "${LOGFILE}" | egrep -q "Processing queue item"; then
    fail "Should not have processed anything out of an empty file"
  fi
  if [[ -f "${QUEUE}" ]]; then
    fail "Queue '${QUEUE}' should have been consumed"
  fi
else 
    fail "Failed to process empty queue"
fi

echo -n "" > "${LOGFILE}"
cp test-staging/hello.jar test-staging/hello.queued.1.jar
echo "42:user:test-staging/hello.queued.1.jar:now:output-staging::java8" > "${QUEUE}"
if "${SCRIPT_REALPATH}/../sign_queue_process.sh"; then
  if ! cat "${LOGFILE}" | egrep -q "Processing queue item '.*hello.queued.1.jar'"; then
    fail "Should have processed hello.queued.1.jar"
  elif ! jar_is_signed output-staging/hello.queued.1.jar; then
    fail "test-staging/hello.queued.1.jar should be signed"
  fi
  if [[ -f "${QUEUE}" ]]; then
    fail "Queue '${QUEUE}' should have been consumed"
  fi
else 
    fail "Failed to process queue with 1 element"
fi

echo -n "" > "${LOGFILE}"
cp test-staging/hello.jar test-staging/hello.queued.1.jar
cp test-staging/hello.jar test-staging/hello.queued.2.jar
echo "42:user:test-staging/hello.queued.1.jar:now:output-staging::java8" > "${QUEUE}"
echo "42:user:test-staging/hello.queued.2.jar:now:output-staging::java6" >> "${QUEUE}"
if "${SCRIPT_REALPATH}/../sign_queue_process.sh"; then
  if ! cat "${LOGFILE}" | egrep -q "Processing queue item '.*hello.queued.1.jar'"; then
    fail "Should have processed hello.queued.1.jar"
  elif ! jar_is_signed output-staging/hello.queued.1.jar; then
    fail "test-staging/hello.queued.1.jar should be signed"
  fi
  if ! cat "${LOGFILE}" | egrep -q "Processing queue item '.*hello.queued.2.jar'"; then
    fail "Should have processed hello.queued.2.jar"
  elif ! jar_is_signed output-staging/hello.queued.2.jar; then
    fail "test-staging/hello.queued.2.jar should be signed"
  fi
  if [[ -f "${QUEUE}" ]]; then
    fail "Queue '${QUEUE}' should have been consumed"
  fi
else 
    fail "Failed to process queue with 2 elements"
fi

echo "[SUCCESS] All tests passed [${SCRIPT_REALNAME}]"