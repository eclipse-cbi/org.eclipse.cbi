
.PHONY: clean jar-signing-service authenticode-signing macos

jar-signing:
	./mvnw verify -am -pl webservice/signing/jar
	./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet
	./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet

authenticode-signing:
	./mvnw verify -am -pl webservice/signing/windows
	./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet

macos:
	jsonnet webservice/signing/macosx/services.jsonnet | jq -r '.["kube.yml"]'  | kubectl apply -f -
	jsonnet webservice/packaging/dmg/services.jsonnet | jq -r '.["kube.yml"]'  | kubectl apply -f -

clean:
	./mvnw clean