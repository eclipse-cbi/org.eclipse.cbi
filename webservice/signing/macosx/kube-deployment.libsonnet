local newDeployment(name, namespace) = {
  name: name,
  namespace: namespace,

  local stagingName(name) = "%s-staging" % name,
  local labels(name) = {
    "org.eclipse.cbi.service/name": name,
  },
  local metaData(name) = {
    name: name,
    labels: labels(name),
    namespace: namespace,
  },

  route: {
    apiVersion: "route.openshift.io/v1",
    kind: "Route",
    metadata: metaData(name) + {
      annotations: {
        "haproxy.router.openshift.io/timeout": "600s",
        "haproxy.router.openshift.io/rewrite-target": "/macosx-signing-service"
      },
    },
    spec: {
      host: "cbi.eclipse.org",
      path: "/macos/codesign/sign",
      port: {
        targetPort: "http"
      },
      tls: {
        insecureEdgeTerminationPolicy: "Redirect",
        termination: "edge"
      },
      to: {
        kind: "Service",
        name: name,
        weight: 100
      },
    }
  },
  stagingroute: {
    apiVersion: "route.openshift.io/v1",
    kind: "Route",
    metadata: metaData(stagingName(name)) + {
      annotations: {
        "haproxy.router.openshift.io/timeout": "600s",
        "haproxy.router.openshift.io/rewrite-target": "/macosx-signing-service"
      },
    },
    spec: {
      host: "cbi-staging.eclipse.org",
      path: "/macos/codesign/sign",
      port: {
        targetPort: "http"
      },
      tls: {
        insecureEdgeTerminationPolicy: "Redirect",
        termination: "edge"
      },
      to: {
        kind: "Service",
        name: stagingName(name),
        weight: 100
      },
    }
  },
  service: {
    apiVersion: "v1",
    kind: "Service",
    metadata: metaData(name),
    spec: {
      type: "ClusterIP",
      ports: [
        {
          name: "http",
          port: 80,
          protocol: "TCP",
          targetPort: 8282
        }
      ],
    }
  },
  stagingservice: {
    apiVersion: "v1",
    kind: "Service",
    metadata: metaData(stagingName(name)),
    spec: {
      type: "ClusterIP",
      ports: [
        {
          name: "http",
          port: 80,
          protocol: "TCP",
          targetPort: 6262
        }
      ],
    }
  },
  endpoints: {
    apiVersion: "v1",
    kind: "Endpoints",
    metadata: metaData(name),
    subsets: [
      {
        addresses: [
          {
            ip: "172.30.206.145"
          },
          {
            ip: "172.30.206.146"
          },
        ],
        ports: [
          {
            name: "http",
            port: 8282,
            protocol: "TCP"
          }
        ]
      }
    ]
  },
  stagingendpoints: {
    apiVersion: "v1",
    kind: "Endpoints",
    metadata: metaData(stagingName(name)),
    subsets: [
      {
        addresses: [
          {
            ip: "172.30.206.145"
          },
          {
            ip: "172.30.206.146"
          },
        ],
        ports: [
          {
            name: "http",
            port: 6262,
            protocol: "TCP"
          }
        ]
      }
    ]
  },
  "kube.yml": std.manifestYamlStream([$.route, $.service, $.endpoints, $.stagingroute, $.stagingservice, $.stagingendpoints], true, c_document_end=false),
};
{
  newDeployment:: newDeployment,
}

