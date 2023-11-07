
.PHONY: all clean jar-signing-service authenticode-signing macos

all: jar-signing authenticode-signing macos

jar-signing: clean
	./mvnw verify -am -pl webservice/signing/jar
	./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet
	./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet

authenticode-signing: clean
	./mvnw verify -am -pl webservice/signing/windows
	./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet

macos: clean
	jsonnet webservice/signing/macosx/services.jsonnet | jq -r '.["kube.yml"]'  | kubectl apply -f -
	jsonnet webservice/packaging/dmg/services.jsonnet | jq -r '.["kube.yml"]'  | kubectl apply -f -

clean:
	./mvnw clean
