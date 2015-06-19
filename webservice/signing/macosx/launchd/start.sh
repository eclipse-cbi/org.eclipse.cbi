#!/bin/bash
###############################################################################
# Copyright (c) 2015 Eclipse Foundation and others
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#   Mikaël Barbero - initial implementation
###############################################################################


JAR_FILE=$(find . -name "macosx-signing-service*.jar" | sort | tail -n 1)

$(/usr/libexec/java_home -v 1.8)/bin/java -jar "${JAR_FILE}"