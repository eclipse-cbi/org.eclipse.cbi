local deployment = import "../../deployment.libsonnet";

deployment.newDeployment("macosx-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/macos/codesign/sign",
  preDeploy: importstr "../keychain.sh",

  docker+: {
    registry: "ghcr.io",
    repository: "eclipse-cbi",
  },

  keystore: {
    type: "APPLE",
    path: "/var/run/secrets/%s/" % $.kube.serviceName,
    volumeName: "keystore",
    secretName: "%s-keystore" % $.kube.serviceName,
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

  rcodesignPath: "/usr/local/bin/rcodesign",

  Dockerfile: super.Dockerfile + |||
    RUN cd /usr/local/bin \
      && curl -L -o codesign.tar.gz 'https://github.com/indygreg/apple-platform-rs/releases/download/apple-codesign%2F0.27.0/apple-codesign-0.27.0-x86_64-unknown-linux-musl.tar.gz' \
      && tar xzf codesign.tar.gz --strip-components=1 \
      && rm -f codesign.tar.gz
  ||| % $,

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
      # The actual codesigner implementation to use
      macosx.codesigner=RCODESIGNER

      ##
      # Mandatory
      macosx.rcodesign=%(rcodesignPath)s

      ##
      # Mandatory
      # The keychain containing the application certificate
      macosx.identity.application.keychain=%(applicationKeychain)s

      ##
      # Mandatory
      # The password for application keychain
      macosx.identity.application.keychain.password-file=%(applicationPasswordFile)s

      ##
      # Mandatory
      # The keychain containing the installer certificate
      macosx.identity.installer.keychain=.%(installerKeychain)s

      ##
      # Mandatory
      # The password for installer keychain
      macosx.identity.installer.keychain.password-file=%(installerPasswordFile)s

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
