# Description:

sign: entry point. 
  Take a jar or a zip, an output dir, and a directive about how to queue the process (mail, nomail, now)
  It builds a queue line (see below for the format)
  If "now", netcat to SIGN_SERVER_HOSTNAME:SIGN_SERVER_PORT, otherwise write the line to QUEUE
  
sign_queue_process.sh: cronjob and xinetd service
  * if started from xinetd, read stdin for the queueline, and process it
  * if started from cron, copy the QUEUE to a new queue (filename + PPID of the current run),
  it iterates on all lines on this new queue file and process them
  
  processing a queue line is about spliting the parameter, and calling the jar processor on it.
  The jar processor will itself call a sign script.
  
  Java version synchronization is done through the usage of jar_processor_signer_javaX symlinks, and 
  all path are configured in "config" file.
  
  
  

# Format of the Queue file

date:username:filetobesigned:queueoption:outputdir:skiprepack:java_version

queueoption= mail|nomail|now
skiprepack=empty or skiprepack
java_version=java[0-9]+