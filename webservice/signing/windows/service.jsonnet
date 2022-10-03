local deployment = import "../../deployment.libsonnet";

deployment.newDeployment("authenticode-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/authenticode/sign",
  osslsigncodeRepo: "https://github.com/mtrojnar/osslsigncode",
  osslsigncodeTag: "2.5", # the tag of osslsigncode to build and deploy
  preDeploy: importstr "../keystore.sh",
  keystore: {
    path: "/var/run/secrets/%s/" % $.name,
    filename: "keystore.p12",
    volumeName: "keystore",
    secretName: "%s-keystore" % $.name,
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.keystore.passwd",
      filename: "keystore.passwd",
    },
    entries: [
      {
        name: "eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/digicert-root.crt", },
          { pass: "IT/CBI/PKI/codesigning/digicert-codesigning.crt", },
          { pass: "IT/CBI/PKI/codesigning/eclipse.org.crt", },
        ],
        privateKey: {
          pass: "IT/CBI/PKI/codesigning/eclipse.org-4k.pkcs8.pem",
        },
      },
    ],
  },

  kube+: {
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
  osslsigncodePath: "/usr/local/bin",
  Dockerfile: |||
    FROM %(builderImage)s AS builder

    RUN apt-get update && apt-get -y --no-install-recommends install \
        build-essential \
        ca-certificates \
        cmake \
        git \
        libcurl4-openssl-dev \
        libssl-dev \
      && rm -rf /var/lib/apt/lists/*

    RUN git clone %(osslsigncodeRepo)s \
      && cd osslsigncode \
      && git checkout tags/%(osslsigncodeTag)s -b %(osslsigncodeTag)s \
      && mkdir build \
      && cd build \
      && cmake -S .. \
      && cmake --build .
  ||| % $ { builderImage: $.docker.baseImage } + super.Dockerfile + |||
    RUN apt-get update && apt-get -y --no-install-recommends install \
      libcurl4 \
      libssl3
    COPY --from=builder /osslsigncode/build/osslsigncode %(osslsigncodePath)s/
  ||| % $,

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
      windows.osslsigncode=%(osslsigncodePath)s/osslsigncode

      ##
      # Mandatory
      ##
      windows.osslsigncode.pkcs12=%(keystoreFile)s

      ##
      # Mandatory
      ##
      windows.osslsigncode.pkc12.password=%(keystorePasswdFile)s

      ##
      # Mandatory
      ##
      windows.osslsigncode.description=Eclipse Foundation, Inc.

      ##
      # Mandatory
      ##
      windows.osslsigncode.url=http://www.eclipse.org/

      ##
      # Mandatory
      ##
      windows.osslsigncode.timestampurl=http://timestamp.comodoca.com/


      ### Log4j configuration section

      # Root logger option
      log4j.rootLogger=INFO, file

      # Redirect log messages to a log file, support file rolling.
      log4j.appender.file=org.apache.log4j.RollingFileAppender
      log4j.appender.file.File=%(logFolder)s/server.log
      log4j.appender.file.MaxFileSize=10MB
      log4j.appender.file.MaxBackupIndex=10
      log4j.appender.file.layout=org.apache.log4j.PatternLayout
      log4j.appender.file.layout.ConversionPattern=%%d{yyyy-MM-dd HH:mm:ss} %%-5p %%c{1}:%%L - %%m%%n
    ||| % $ {keystoreFile: "%s/%s" % [ $.keystore.path, $.keystore.filename ], keystorePasswdFile: "%s/%s" % [ $.keystore.path, $.keystore.password.filename ] },
  },
}
