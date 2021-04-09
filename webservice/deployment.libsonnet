local newDeployment(name, artifactId, version) = {
  name: name,
  version: version,
  groupId: "org.eclipse.cbi",
  artifactId: artifactId,
  mavenRepoURL: "repo.eclipse.org",
  mavenRepoName: "cbi",
  port: 8080,
  docker: {
    registry: "docker.io",
    repository: "eclipsecbi",
    baseImage: "adoptopenjdk:11-jdk-hotspot",
    imageName: $.name,
    tag: $.version,
    image: "%s/%s/%s:%s" % [self.registry, self.repository, self.imageName, self.tag,],
  },
  tempFolder: "/tmp/%s" % self.name,
  pathspec: "/%s/%s" % [self.name, self.version],
  logFolder: "/var/log/%s" % self.name,
  configuration: {
    path: "/etc/%s" % $.name,
    filename: "default.properties",
    content: error "No configuration content",
  },
  preDeploy: "echo 'Nothing to do during preDeploy'",
  postDeploy: "echo 'Nothing to do during postDeploy'",
  kube: {
    namespace: "foundation-internal-infra-apps",
    servicePortName: "http",
    configMapName: nameByEnv($.name),
    serviceName: nameByEnv($.name),
    local nameByEnv(name) = (
      if std.endsWith($.version, "SNAPSHOT") then "%s-staging" else "%s"
    ) % name,
    local labels(name) = {
      "org.eclipse.cbi.service/name": name,
    },
    local metadata(name) = {
      name: name,
      namespace: $.kube.namespace,
      labels: labels(name),
      annotations: {
        "org.eclipse.cbi.service/version": $.version,
      },
    },
    metadata:: metadata,
    resources: [
      {
        apiVersion: "apps/v1",
        kind: "Deployment",
        metadata: metadata(nameByEnv($.name)),
        spec: {
          selector: {
            matchLabels: labels(nameByEnv($.name)),
          },
          replicas: if std.endsWith($.version, "SNAPSHOT") then 1 else 2,
          template: {
            metadata: {
              labels: labels(nameByEnv($.name)),
              annotations: {
                "org.eclipse.cbi.service/version": $.version,
              },
            },
            spec: {
              affinity: {
                nodeAffinity: {
                  preferredDuringSchedulingIgnoredDuringExecution: [{
                    preference: {
                      matchExpressions: [{
                        key: "speed",
                        operator: "NotIn",
                        values: ["fast",]
                      }]
                    },
                    weight: 1
                  }]
                },
              },
              containers: [
                {
                  name: "service",
                  image: $.docker.image,
                  imagePullPolicy: "Always",
                  ports: [
                    {
                      containerPort: $.port,
                    }
                  ],
                  resources: {
                    limits: {
                      cpu: if std.endsWith($.version, "SNAPSHOT") then "1" else "4",
                      memory: if std.endsWith($.version, "SNAPSHOT") then "1Gi" else "2Gi"
                    },
                    requests: {
                      cpu: if std.endsWith($.version, "SNAPSHOT") then "50m" else "500m",
                      memory: if std.endsWith($.version, "SNAPSHOT") then "1Gi" else "2Gi"
                    },
                  },
                  livenessProbe: {
                    failureThreshold: 3,
                    httpGet: {
                      path: "/heartbeat",
                      port: $.port,
                      scheme: "HTTP"
                    },
                    initialDelaySeconds: 60,
                    periodSeconds: 30,
                    timeoutSeconds: 10
                  },
                  readinessProbe: {
                    failureThreshold: 3,
                    httpGet: {
                      path: "/heartbeat",
                      port: $.port,
                      scheme: "HTTP"
                    },
                    initialDelaySeconds: 5,
                    periodSeconds: 10,
                    timeoutSeconds: 10
                  },
                  volumeMounts: [
                    {
                      mountPath: $.configuration.path,
                      name: "etc",
                      readOnly: true
                    }, {
                      mountPath: $.tempFolder,
                      name: "tmp"
                    }, {
                      mountPath: $.logFolder,
                      name: "log"
                    },
                  ],
                }
              ],
              volumes: [
                {
                  emptyDir: {},
                  name: "tmp"
                },
                {
                  emptyDir: {},
                  name: "log"
                },
                {
                  configMap: {
                    name: $.kube.configMapName
                  },
                  name: "etc"
                },
              ]
            }
          }
        }
      },
      {
        apiVersion: "v1",
        kind: "Service",
        metadata: metadata(nameByEnv($.name)),
        spec: {
          ports: [{
            name: $.kube.servicePortName,
            port: 80,
            protocol: "TCP",
            targetPort: $.port,
          }],
          selector: labels(nameByEnv($.name)),
        }
      },
      {
        apiVersion: "route.openshift.io/v1",
        kind: "Route",
        metadata: metadata(nameByEnv($.name)) + {
          annotations: {
            "haproxy.router.openshift.io/timeout": "60s"
          },
        },
        spec: {
          host: if std.endsWith($.version, "SNAPSHOT") then "cbi-staging.eclipse.org" else "cbi.eclipse.org",
          path: $.pathspec,
          port: {
            targetPort: $.kube.servicePortName
          },
          tls: {
            insecureEdgeTerminationPolicy: "Redirect",
            termination: "edge"
          },
          to: {
            kind: "Service",
            name: $.kube.serviceName,
            weight: 100
          }
        },
      },
      {
        apiVersion: "v1",
        kind: "ConfigMap",
        metadata: metadata($.kube.configMapName),
        data: {
          [$.configuration.filename]: $.configuration.content,
        }
      },
    ],
  },
  # we will copy artifact from target/ folder if current version is SNAPSHOT, otherwise, download it from repo.eclipse.org
  Dockerfile: if std.endsWith($.version, "SNAPSHOT") then |||
    FROM %(from)s

    COPY target/%(artifactId)s-%(version)s.jar /usr/local/%(name)s/

    ENTRYPOINT [ "java", \
      "-showversion", "-XshowSettings:vm", "-Xmx512m", \
      "-jar", "/usr/local/%(name)s/%(artifactId)s-%(version)s.jar", \
      "-c", "%(configurationPath)s/%(configurationFilename)s" \
    ]
  ||| % self { from: $.docker.baseImage, configurationPath: $.configuration.path, configurationFilename: $.configuration.filename }
  else |||
    FROM %(from)s

    RUN mkdir -p /usr/local/%(name)s/ \
      && curl -sSLf -o /usr/local/%(name)s/%(artifactId)s-%(version)s.jar "https://%(mavenRepoURL)s/service/local/artifact/maven/content?r=%(mavenRepoName)s&g=%(groupId)s&a=%(artifactId)s&v=%(version)s"

    ENTRYPOINT [ "java", \
      "-showversion", "-XshowSettings:vm", "-Xmx512m", \
      "-jar", "/usr/local/%(name)s/%(artifactId)s-%(version)s.jar", \
      "-c", "%(configurationPath)s/%(configurationFilename)s" \
    ]
  ||| % self { from: $.docker.baseImage, configurationPath: $.configuration.path, configurationFilename: $.configuration.filename },

  "kube.yml": std.manifestYamlStream($.kube.resources, true, c_document_end=false),
};
{
  newDeployment:: newDeployment,
}