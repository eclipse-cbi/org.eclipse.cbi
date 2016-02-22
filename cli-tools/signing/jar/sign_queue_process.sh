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

# sign_queue_process.sh: Run as a cronjob, this script processes the signing queue
# and invokes jarprocessor.jar (and sign.sh) for files that need to be signed.

umask 0007

PID=$$

HOSTNAME=$(hostname)
LOGFILE=/home/data/httpd/download-staging.priv/arch/signer.log
LOCKFILE=/home/data/httpd/download-staging.priv/arch/signing_queue.lock.$PID

# read stdin to see if we need to sign now
read -t 2 stdin

if [ -n "$stdin" ]; then
	STARTDATE=$(date +%Y-%m-%d\ %H:%M:%S)
        echo "$STARTDATE: $HOSTNAME QUEUE($$): Begin processing queue NOW: $$" >> $LOGFILE
        echo "$stdin" > $LOCKFILE
else
	if [ -f /home/data/httpd/download-staging.priv/arch/signing_queue ]; then
		if [ $(awk -F. '{print $1}' /proc/loadavg) -gt 40 ]; then echo "Too busy to sign.  Going to sleep."; exit 128; fi

		STARTDATE=$(date +%Y-%m-%d\ %H:%M:%S)

		mv /home/data/httpd/download-staging.priv/arch/signing_queue $LOCKFILE 
		echo "$STARTDATE: $HOSTNAME QUEUE($$): Begin processing queue: $$" >> $LOGFILE
	fi
fi

# Do we have a queue?
if [ -f $LOCKFILE ]; then
	cat $LOCKFILE >> $LOGFILE
	echo "======================= end of ($$) contents" >> $LOGFILE
	for i in $(cat $LOCKFILE); do
		FILE=$(echo -n $i | awk -F: {'print $3'})
		DIR=$(echo -n $i | awk -F: {'print $5'})
		if [ "$DIR" = "skiprepack" ]; then
			DIR=""
		fi
		SKIPREPACK=$(echo -n $i | grep -o "skiprepack")
		REPACK=""
		if [ -z "$SKIPREPACK" ]; then
			REPACK="-repack"
		fi
		# /home/admin/sign.sh $FILE 

		# Bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=135044

		if [ -z "$DIR" ]; then
			DIR=$(dirname "$FILE")
		fi

		# Check for files on build2 ... otherwise they're on build
		if [ ! -e "$FILE" ]; then
			FILE=$(echo $FILE | sed -e 's#/opt/users/hudsonbuild/.hudson/jobs#/opt/public/jobs#')
		fi
		if [ ! -e "$DIR" ]; then
			DIR=$(echo $DIR | sed -e 's#/opt/users/hudsonbuild/.hudson/jobs#/opt/public/jobs#')
		fi

	
		# /shared/common/ibm-java2-ppc-50/jre/bin/java /home/admin/jarprocessor.jar -outputDir $DIR $REPACK -verbose -processAll -sign /home/admin/sign.sh $FILE
		NOWDATE=$(date +%Y-%m-%d\ %H:%M:%S)
		echo "$NOWDATE: $HOSTNAME QUEUE($$): calling jarprocessor.jar for [$FILE]" >> $LOGFILE
		#Mward 09/20/10 /shared/common/ibm-java2-ppc-50/jre/bin/java -jar /home/admin/jarprocessor.jar -outputDir $DIR $REPACK -verbose -processAll -sign ~/signing/sign.sh $FILE >> $LOGFILE 2>&1
		# echo /opt/public/common/ibm-java-x86_64-60/bin/java -jar /home/admin/jarprocessor.jar -outputDir $DIR $REPACK -verbose -processAll -sign /home/data/users/genie/signing/sign.sh  $FILE >> $LOGFILE 2>&1
		
		JAVA8=$(echo -n $i | grep -o "java8")
                if [ -n "$JAVA8" ]; then
		    echo "$NOWDATE: $HOSTNAME QUEUE($$): Using Java8 to run jarprocessor and signing" >> $LOGFILE
		    JAVA_DIR=/opt/public/common/jdk1.8.0_x64-latest
	   	    JARPROCESSOR=/home/data/users/genie/signing/org.eclipse.equinox.p2.jarprocessor_1.0.300.v20131211-1531.jar
		    SIGNSCRIPT=/home/data/users/genie/signing/sign8.sh
                else
		    echo "$NOWDATE: $HOSTNAME QUEUE($$): Using Java6 to run old jarprocessor and signing" >> $LOGFILE
		    JAVA_DIR=/opt/public/common/ibm-java-x86_64-60
		    JARPROCESSOR=/home/data/users/genie/signing/jarprocessor.jar
		    SIGNSCRIPT=/home/data/users/genie/signing/sign.sh
		fi
	        echo "$JAVA_DIR/bin/java -jar $JARPROCESSOR -outputDir $DIR $REPACK -verbose -processAll -sign /home/data/users/genie/signing/sign.sh  $FILE $JAVA8"  >> $LOGFILE
		$JAVA_DIR/bin/java -jar $JARPROCESSOR -outputDir $DIR $REPACK -verbose -processAll -sign $SIGNSCRIPT  $FILE  >> $LOGFILE 2>&1


#		$JAVA_DIR/bin/java -jar /home/admin/jarprocessor.jar -outputDir $DIR $REPACK -verbose -processAll -sign /home/data/users/genie/signing/sign.sh  $FILE  >> $LOGFILE 2>&1
		OUTPUTFILE=$(basename "$FILE")
		DESTGROUP=$(ls -l $FILE | awk {'print $4'})
	        #alter ownership to match that of the source file
                chgrp $DESTGROUP $DIR/$OUTPUTFILE 2>/dev/null
	done
	ENDDATE=$(date +%Y-%m-%d\ %H:%M:%S)
	echo "$ENDDATE: $HOSTNAME QUEUE($$): Finished processing queue." >> $LOGFILE

	# If we asked to sign now, report back to STDIN
	if [ -n "$stdin" ]; then
		echo "Finished signing $FILE."
	fi

	# send notification e-mails
	for i in $(cat $LOCKFILE | grep ":mail:" | awk -F: {'print $2'} | sort | uniq); do
		USERID=$i
		#updating the final awk to catch users like dmadruga that have more than a first and last name
		#M. Ward 06/10/09
                #MAIL=$(getent passwd | grep $USERID | awk -F: {'print $5'} | awk {'print $NF'})
		#With the email adress removed from teh gecos field this line needed to be updated. M. Ward 02/08/11
                MAIL=$(ldapsearch -LLL -x  "uid=$USERID" mail | awk {'getline;print $2'})

                echo "One or more files placed in the signing queue are now signed." | /usr/bin/mail -s "File Signing Complete" -r "webmaster@eclipse.org" $MAIL
        done

	rm $LOCKFILE
	#echo -n "End signing at "
	#date
fi
