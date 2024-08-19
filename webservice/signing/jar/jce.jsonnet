local jarsigner = import "jarsigner.libsonnet";

jarsigner.newDeployment("jar-signing-jce", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/jarsigner/jce/sign",

  keystore+: {
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.keystore.passwd",
      filename: "keystore.passwd",
    },
    defaultAlias: "jce-eclipse.org",
    entries: [
      {
        name: "jce-eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/oracle-jce-codesigning-root.crt", },
          { pass: "IT/CBI/PKI/codesigning/eclipse.org-jce.crt", },
        ],
        privateKey: {
          pass: "IT/CBI/PKI/codesigning/eclipse.org-4k.pkcs8.pem",
        },
      },
    ],
  },

  configuration+: {
    content: |||
      ##
      # Optional (default = 8080)
      ##
      server.port=%(port)s

      ##
      # Mandatory
      ##
      server.access.log=%(logFolder)s/access-yyyy_mm_dd.log

      ##
      # Mandatory
      # Must be an absolute path
      ##
      server.temp.folder=%(tempFolder)s

      ##
      # Mandatory
      # The path that will offer the service. The version of the
      # service will be appended (if server.service.pathspec.versioned
      # is set to true, i.e. if you set it to /service and the current
      # version is 1.3.0-SNAPSHOT, the service will be offered on
      # http://server:${server.port}/service/1.3.0-SNAPSHOT
      ##
      server.service.pathspec=%(pathspec)s

      ##
      # Optional, boolean (default = true)
      # Control whether the service version will be appended to
      # server.service.pathspec
      ##
      server.service.pathspec.versioned=false

      ##
      # Mandatory
      ##
      jarsigner.bin=/opt/java/openjdk11/bin/jarsigner

      ##
      # Mandatory
      ##
      jarsigner.keystore=%(keystoreFile)s

      ##
      # Optional
      # The default keystore type is the one that is specified as the value
      # of the keystore.type property in the security properties file. The
      # security properties file is called java.security, and it resides in
      # the JDK security properties directory, <java.home>/lib/security)
      ##
      jarsigner.storetype=PKCS12

      ##
      # Mandatory
      ##
      jarsigner.keystore.password=%(keystorePasswdFile)s

      ##
      # Mandatory
      ##
      jarsigner.keystore.alias=%(keystoreDefaultAlias)s

      ##
      # Mandatory
      ##
      jarsigner.tsa=http://timestamp.digicert.com

      ##
      # Proxies: optional, default = none
      ##
      # jarsigner.http.proxy.host=proxy.eclipse.org
      # jarsigner.http.proxy.port=9898
      # jarsigner.https.proxy.host=proxy.eclipse.org
      # jarsigner.https.proxy.port=9898

      ##
      # Optional (default = 2min = 120sec)
      # In seconds
      ##
      jarsigner.timeout=120

      ### Log4j configuration section

      # Root logger option
      log4j.rootLogger=INFO, console, file

      log4j.appender.console=org.apache.log4j.ConsoleAppender
      log4j.appender.console.layout=org.apache.log4j.PatternLayout
      log4j.appender.console.layout.ConversionPattern=%%d{yyyy-MM-dd HH:mm:ss} %%-5p %%c{1}:%%L - %%m%%n

      # Redirect log messages to a log file, support file rolling.
      log4j.appender.file=org.apache.log4j.RollingFileAppender
      log4j.appender.file.File=%(logFolder)s/server.log
      log4j.appender.file.MaxFileSize=10MB
      log4j.appender.file.MaxBackupIndex=10
      log4j.appender.file.layout=org.apache.log4j.PatternLayout
      log4j.appender.file.layout.ConversionPattern=%%d{yyyy-MM-dd HH:mm:ss} %%-5p %%c{1}:%%L - %%m%%n
    ||| % $ {
      keystoreFile: "%s/%s" % [ $.keystore.path, $.keystore.filename ],
      keystorePasswdFile: "%s/%s" % [ $.keystore.path, $.keystore.password.filename ],
      keystoreDefaultAlias: $.keystore.defaultAlias,
    },
  },
}
