local deployment = import "../../deployment.libsonnet";

deployment.newDeployment("authenticode-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/authenticode/sign",
  preDeploy: importstr "../keystore.sh",

  docker+: {
    registry: "ghcr.io",
    repository: "eclipse-cbi",
  },

  keystore: {
    type: "GOOGLECLOUD",
    path: "/var/run/secrets/%s/" % $.kube.serviceName,
    volumeName: "keystore",
    secretName: "%s-keystore" % $.kube.serviceName,
    filename: "certchain.pem",
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.gcloud-credentials.json",
      filename: "gcloud-credentials.json",
    },
    keyRing: "projects/hsm-codesigning/locations/global/keyRings/eclipse_org",
    defaultAlias: "codesigning-key/cryptoKeyVersions/1:RSA",
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

  kube+: {
    namespace: "foundation-codesigning",
    resources: [
      if resource.kind == "Deployment" then resource + {
        spec+: {
          template+: {
            spec+: {
              containers: [
                if container.name == "service" then container + {
                  volumeMounts+: [
                    {
                      mountPath: $.keystore.path,
                      name: $.keystore.volumeName,
                      readOnly: true
                    },
                  ],
                } else container for container in super.containers
              ],
              volumes+: [
                {
                  name: $.keystore.volumeName,
                  secret: {
                    secretName: $.keystore.secretName,
                  },
                },
              ],
            },
          },
        },
      } else resource for resource in super.resources
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
      windows.jsign.description=Eclipse Foundation, Inc.

      ##
      # Mandatory
      ##
      windows.jsign.url=http://www.eclipse.org/

      ##
      # Mandatory
      # The actual codesigner implementation to use
      windows.codesigner=JSIGN

      ##
      # Mandatory
      # The storetype to use
      windows.jsign.storetype=GOOGLECLOUD

      ##
      # Mandatory
      # The path to the keystore to use
      windows.jsign.keystore=%(keyRing)s

      ##
      # Mandatory
      # the actual key name
      windows.jsign.keyalias=%(defaultKey)s

      ##
      # Mandatory
      # the actual path of the certificate
      windows.jsign.certchain=%(certChainFile)s

      ##
      # Mandatory
      # the credentials to connect to Google KMS
      windows.jsign.kms.credentials=%(credentialsFile)s

      ##
      # Mandatory
      ##
      windows.jsign.timestampurl.1=http://timestamp.digicert.com
      windows.jsign.timestampurl.2=http://timestamp.sectigo.com
      windows.jsign.timestampurl.3=http://timestamp.globalsign.com/?signature=sha2

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
      credentialsFile: "%s/%s" % [ $.keystore.path, $.keystore.password.filename ],
      certChainFile: "%s/%s" % [ $.keystore.path, $.keystore.filename ],
      keyRing: $.keystore.keyRing,
      defaultKey: $.keystore.defaultAlias,
    },
  },
}
