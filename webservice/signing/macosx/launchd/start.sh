#!/bin/bash
###############################################################################
# Copyright (c) 2015 Eclipse Foundation and others
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


JAR_FILE=$(find . -name "macosx-signing-service*.jar" | sort | tail -n 1)

$(/usr/libexec/java_home -v 1.8)/bin/java -jar "${JAR_FILE}"