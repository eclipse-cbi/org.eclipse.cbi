local newDeployment() = {
  route: {
    apiVersion: "route.openshift.io/v1",
    kind: "Route",
    metadata: {
      annotations: {
        "haproxy.router.openshift.io/timeout": "600s",
        "haproxy.router.openshift.io/rewrite-target": "/dmg-packaging-service"
      },
      name: "macos-dmg-packaging",
      namespace: "foundation-internal-infra-apps"
    },
    spec: {
      host: "cbi.eclipse.org",
      path: "/macos/packager/dmg/create",
      port: {
        targetPort: "http"
      },
      tls: {
        insecureEdgeTerminationPolicy: "Redirect",
        termination: "edge"
      },
      to: {
        kind: "Service",
        name: "macos-dmg-packaging",
        weight: 100
      },
    }
  },
  service: {
    apiVersion: "v1",
    kind: "Service",
    metadata: {
      name: "macos-dmg-packaging",
      namespace: "foundation-internal-infra-apps"
    },
    spec: {
      type: "ClusterIP",
      ports: [
        {
          name: "http",
          port: 80,
          protocol: "TCP",
          targetPort: 8787
        }
      ],
    }
  },
  endpoints: {
    apiVersion: "v1",
    kind: "Endpoints",
    metadata: {
      name: "macos-dmg-packaging",
      namespace: "foundation-internal-infra-apps"
    },
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
            port: 8787,
            protocol: "TCP"
          }
        ]
      }
    ]
  },
  "kube.yml": std.manifestYamlStream([$.route, $.service, $.endpoints], true, c_document_end=false),
};
{
  newDeployment:: newDeployment,
}

