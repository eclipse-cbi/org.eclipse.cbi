local jarsigner = import "jarsigner.libsonnet";

jarsigner.newDeployment("jar-signing-jce", std.extVar("artifactId"), std.extVar("version")) {
  pathspec: "/jarsigner/jce/sign",
  keystore+: {
    password: {
      pass: "IT/CBI/PKI/codesigning/eclipse.org.keystore.passwd",
      filename: "keystore.passwd",
    },
    defaultAlias: "jce-eclipse.org",
    entries: [
      {
        name: "jce-eclipse.org",
        certificates: [
          { pass: "IT/CBI/PKI/codesigning/sun-jce-codesigning-root.crt", },
          { pass: "IT/CBI/PKI/codesigning/eclipse.org-jce.crt", },
        ],
        privateKey: {
          pass: "IT/CBI/PKI/codesigning/eclipse.org.pkcs8.pem",
        },
      },
    ],
  },
}
