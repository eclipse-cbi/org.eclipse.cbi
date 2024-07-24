local deployment = import "../../deployment.libsonnet";

local newDeployment(name, artifactId, version) = deployment.newDeployment(name, artifactId, version) {
  pathspec: error "Must specify a pathspec for a service deployment",
  preDeploy: importstr "../keystore.sh",

  docker+: {
    registry: "ghcr.io",
    repository: "eclipse-cbi",
  },

  keystore: {
    path: "/var/run/secrets/%s/" % $.kube.serviceName,
    filename: "keystore.p12",
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
};

{
  newDeployment:: newDeployment
}