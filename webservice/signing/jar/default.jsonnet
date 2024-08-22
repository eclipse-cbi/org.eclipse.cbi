local jarsigner = import "jarsigner.libsonnet";

jarsigner.newDeployment("jar-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/jarsigner/sign",

  keystore+: {
    type: "GOOGLECLOUD",
    filename: "certchain.pem",
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.gcloud-credentials.json",
      filename: "gcloud-credentials.json",
    },
    keyRing: "projects/hsm-codesigning/locations/global/keyRings/eclipse_org",
    defaultAlias: "codesigning-key",
    entries: [
      {
        name: "eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/eclipse.org.crt-2024-2025-KMS", },
          { pass: "IT/CBI/PKI/codesigning/digicert-codesigning.crt-2024-2026", },
          { pass: "IT/CBI/PKI/codesigning/digicert-root.crt", },
        ],
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
      # Optional
      # Capture access log using log4j
      ##
      # server.access.log=%(logFolder)s/access-yyyy_mm_dd.log

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
      # Optional
      ##
      jarsigner.javaargs=-cp %(jarFile)s --add-modules java.sql

      ##
      # Optional
      # Force the sigfile to be named ECLIPSE_ for backwards compatibility
      # unless the sigfile is specified in the incoming request
      ##
      jarsigner.sigfile.default=ECLIPSE_

      ##
      # Optional
      # The default keystore type is the one that is specified as the value
      # of the keystore.type property in the security properties file. The
      # security properties file is called java.security, and it resides in
      # the JDK security properties directory, <java.home>/lib/security)
      ##
      jarsigner.storetype=GOOGLECLOUD

      # Keystore related configurations
      jarsigner.provider.class=net.jsign.jca.JsignJcaProvider
      jarsigner.provider.arg=%(keyRing)s

      jarsigner.kms.credentials=%(credentialsFile)s
      jarsigner.kms.certchain=%(certChainFile)s

      ##
      # Mandatory
      # the actual key name
      ##
      jarsigner.keystore.alias=%(defaultKey)s

      ##
      # Timestamp server
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

      # Capture jetty requests
      log4j.logger.org.eclipse.jetty.server.RequestLog=INFO, console, access-log
      log4j.additivity.org.eclipse.jetty.server.RequestLog=false

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

      # Redirect requests to a separate access log file, support time based file rolling.
      log4j.appender.access-log=org.apache.log4j.rolling.RollingFileAppender
      log4j.appender.access-log.RollingPolicy = org.apache.log4j.rolling.TimeBasedRollingPolicy
      log4j.appender.access-log.RollingPolicy.ActiveFileName = %(logFolder)s/access.log
      log4j.appender.access-log.RollingPolicy.FileNamePattern = %(logFolder)s/access-%%d{yyyy-MM-dd}.log
      log4j.appender.access-log.layout=org.apache.log4j.PatternLayout
      log4j.appender.access-log.layout.ConversionPattern=%%m%%n
    ||| % $ {
      jarFile: "/usr/local/%s/%s-%s.jar" % [ $.name, $.artifactId, $.version ],
      credentialsFile: "%s/%s" % [ $.keystore.path, $.keystore.password.filename ],
      certChainFile: "%s/%s" % [ $.keystore.path, $.keystore.filename ],
      keyRing: $.keystore.keyRing,
      defaultKey: $.keystore.defaultAlias,
    },
  },
}
