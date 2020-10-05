local deployment = import "../../../deployment.libsonnet";

deployment.newDeployment("jar-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/jarsigner/sign",
  preDeploy: importstr "keystore.sh",
  keystore: {
    path: "/var/run/secrets/%s/" % $.name,
    filename: "keystore.p12",
    volumeName: "keystore",
    secretName: "%s-keystore" % $.name,
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.keystore.passwd",
      filename: "keystore.passwd",
    },
    defaultAlias: "eclipse.org",
    entries: [
      {
        name: "eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/digicert-root.cer", },
          { pass: "IT/CBI/PKI/codesigning/digicert-codesigning.cer", },
          { pass: "IT/CBI/PKI/codesigning/eclipse.org.cer", },
        ],
        privateKey: {
          pass: "IT/CBI/PKI/codesigning/eclipse.org.pkcs8.pem",
        },
      },

      {
        name: "jce-eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/digicert-root.cer", },
          { pass: "IT/CBI/PKI/codesigning/digicert-codesigning.cer", },
          { pass: "IT/CBI/PKI/codesigning/eclipse.org.cer", },
        ],
        privateKey: {
          pass: "IT/CBI/PKI/codesigning/eclipse.org.pkcs8.pem",
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
      # Manadatory
      ##
      jarsigner.bin=/opt/java/openjdk/bin/jarsigner

      ##
      # Manadatory
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
      # Manadatory
      ##
      jarsigner.keystore.password=%(keystorePasswdFile)s

      ##
      # Manadatory
      ##
      jarsigner.keystore.alias=%(keystoreDefaultAlias)s

      ##
      # Manadatory
      ##
      jarsigner.tsa=http://sha256timestamp.ws.symantec.com/sha256/timestamp

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
      log4j.rootLogger=INFO, file
      
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
