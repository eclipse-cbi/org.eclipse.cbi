# CBI Maven plugins and digital signing services

This project requires Java 17+.

## Deploy a new released version 

* Build a release
  * https://ci.eclipse.org/cbi/job/org.eclipse.cbi/job/main/ and set `RELEASE_VERSION` and `NEXT_DEVELOPMENT_VERSION` to proper values
* Checkout tag `v$RELEASE_VERSION` and run 
```bash
./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet
./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet
./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet
```

## Deploy an old released version

```bash
VERSION="1.3.0"
./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet "${VERSION}"
./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet "${VERSION}"
./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet "${VERSION}"
```

## Deploy staging version (when version in pom is -SNAPSHOT)

From a laptop with access to deployment cluster
`make clean jar-signing authenticode-signing`

