#!/bin/sh

#*******************************************************************************
#* Copyright (c) 2005, 2007 Eclipse Foundation and others.
#* All rights reserved. This program and the accompanying materials
#* are made available under the terms of the Eclipse Public License v1.0
#* which accompanies this distribution, and is available at
#* http://www.eclipse.org/legal/epl-v10.html
#*
#* Contributors:
#*    Denis Roy (Eclipse Foundation)
#*******************************************************************************/

# sign.sh: called by jarprocessor.jar to invoke jarsigner on a jar file.
# This is run by genie, as a result of processing a file in the signing queue.

SCRIPT_NAME="$(basename ${0})"
SCRIPT_PATH="$(dirname "${0}")"
CONFIG_FILE="${CONFIG_FILE:-/${SCRIPT_PATH}/config}"

JAVA_DIR=/opt/public/common/ibm-java-x86_64-60
source "${CONFIG_FILE}"
STOREPASS=$(cat $STOREPASSFILE)
# ALIASNAME=EclipseFoundation
ALIASNAME=eclipse.org
DOWNLOADSTAGELOC=/home/data/httpd/download-staging.priv
TMPDIR=$DOWNLOADSTAGELOC/arch
LOGFILE=$TMPDIR/signer.log
STARTDATE=$(date +%Y-%m-%d\ %H:%M:%S)
HOSTNAME=$(hostname)
TSASTRING="-tsa https://timestamp.geotrust.com/tsa"
# TSASTRING=""

# Get Grand-parent PID
GPPID=$(cat /proc/$PPID/status | grep "PPid" | awk '{print $2}')

umask 0007
echo "$STARTDATE: $HOSTNAME($UID) SIGNER($GPPID): asked to sign $1" >> $LOGFILE

if [ $UID = 0 ]; then
	echo "Not running as root"
	exit 9
fi

if [ -z "$1" ]; then
	echo "Usage: $0 <file> [target]"
	echo "Signs JAR and ZIP file <file> and places signed file in current directory, or in [target] if specified"
	exit 7
fi


#JAVA8=$(echo -n $i | grep -o "java8")
#if [ -z "$JAVA8" ]; then
#	echo "using java 8"
#	JARSIGNER=/opt/public/common/jdk1.8.0_x64-latest
#else
	JARSIGNER=/opt/public/common/ibm-java-x86_64-60
#fi

JARFILE=$(echo "$1" | grep "\.jar$" | wc -l)
ZIPFILE=$(echo "$1" | grep "\.zip$" | wc -l)
TARGZIPFILE=$(echo "$1" | grep "\.tar\.gz" | wc -l)

# Get directory name from file to ensure it is in the staging area
DIR=$(dirname "$1")
cd "$DIR"

DIR=$PWD
#VALIDLOCATION=$(echo "$DIR" | egrep -c "(\/opt\/public\/download-staging.priv\/|\/home\/data\/httpd\/download-staging.priv|\/opt\/users\/hudsonbuild\/)")


#if [ $VALIDLOCATION = 0 ]; then
#	echo "$HOSTNAME SIGNER($GPPID): File $1 is not in the downloads staging area or the hudson directory. Not signing from unknown location"  >> $LOGFILE
#	exit
#fi

NOWDATE=$(date +%Y-%m-%d\ %H:%M:%S)

if [ ! -f $1 ]; then
	echo "$HOSTNAME SIGNER($GPPID): File $1 not found, or not a valid file."  >> $LOGFILE
	exit 2
fi;

if [ $JARFILE = 0 -a $ZIPFILE = 0 -a $TARGZIPFILE = 0 ]; then
	echo "$HOSTNAME SIGNER($GPPPID): File $1 is not a ZIP, .tar.gz or JAR file.  Cannot continue."  >> $LOGFILE
	exit 3
fi

if [ $JARFILE = 1 -a $ZIPFILE = 1 ]; then
	echo "$HOSTNAME SIGNER($GPPID): File $1 is not a valid ZIP or JAR file.  Cannot continue."  >> $LOGFILE
	exit 1
fi

if [ $JARFILE = 1 ]; then
	#echo "$NOWDATE: $HOSTNAME SIGNER($GPPID): Signing JAR file [$1]" >> $LOGFILE
	# echo $JARSIGNER $TSASTRING -verbose -keystore $KEYSTORE $1 $ALIASNAME  >> $LOGFILE 2>&1
	echo "$NOWDATE: $HOSTNAME SIGNER($GPPID): Signing JAR file [$1] with Java 6" >> $LOGFILE

	$JAVA_DIR/bin/jarsigner $TSASTRING -verbose -keystore $KEYSTORE -storepass $STOREPASS $1 $ALIASNAME  >> $LOGFILE 2>&1
	chmod g+w $1
	ENDDATE=$(date +%Y-%m-%d\ %H:%M:%S)
	echo "$ENDDATE: $HOSTNAME SIGNER($GPPID): Finished signing $1" >> $LOGFILE 
	exit 0
fi

#REMOVED because the jarprocessor script handles all of this.
#if [ $ZIPFILE = 1 ]; then
#	echo "Signing ZIP file $1"
#	echo "This process signs individual JAR files inside a ZIP file. It may take several minutes"
#	# unzip files, sign all jars, re-zip files
#	mkdir $TMPDIR/sign-$PPID
#	unzip -qq "$1" -d $TMPDIR/sign-$PPID
#	for i in $(find $TMPDIR/sign-$PPID -type f -name '*.jar'); do
#		$0 $i
#	done
#	
#	# jars are signed.  Zip it back up and replace original 
#	cd $TMPDIR/sign-$PPID
#	/usr/bin/zip -r "$1" *
#
#	rm -rf $TMPDIR/sign-$PPID
#	exit
#fi

#if [ $TARGZIPFILE = 1 ]; then
#	echo "Signing .tar.gz file $1"
#	echo "This process signs individual JAR files inside a .tar.gz file. It may take several minutes"
#	# unzip files, sign all jars, re-zip files
#	mkdir $TMPDIR/sign-$PPID
#	cd $TMPDIR/sign-$PPID
#	/bin/tar zxf "$1"
#	for i in $(find $TMPDIR/sign-$PPID -type f -name '*.jar'); do
#		$0 $i
#	done
#	
#	# jars are signed.  Zip it back up and replace original 
#	cd $TMPDIR/sign-$PPID
#	/bin/tar czf "$1" *
#
#	rm -rf $TMPDIR/sign-$PPID
#	exit
#fi

exit 0;
