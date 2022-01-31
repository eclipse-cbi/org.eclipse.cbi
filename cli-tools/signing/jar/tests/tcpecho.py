#!/usr/bin/env python
# -*- coding: utf-8 -*-

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

import socket
import sys

if len(sys.argv) != 3:
    print "Usage:", sys.argv[0], "<ip> <port>"
    sys.exit(1)

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((sys.argv[1], int(sys.argv[2])))
sock.listen(1)

conn, addr = sock.accept()
data = conn.recv(4096)
sys.stdout.write(data)

conn.close()
sys.exit(0)