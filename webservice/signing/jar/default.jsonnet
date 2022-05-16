local jarsigner = import "jarsigner.libsonnet";

jarsigner.newDeployment("jar-signing", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/jarsigner/sign",
  keystore+: {
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.keystore.passwd",
      filename: "keystore.passwd",
    },
    defaultAlias: "eclipse.org",
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
}
