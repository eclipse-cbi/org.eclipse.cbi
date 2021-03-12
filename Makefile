
.PHONY: clean jar-signing-service

jar-signing:
	./mvnw verify -am -pl webservice/signing/jar
	./webservice/service-deployment.sh webservice/signing/jar/default.jsonnet
	./webservice/service-deployment.sh webservice/signing/jar/jce.jsonnet

authenticode-signing:
	./mvnw verify -am -pl webservice/signing/windows
	./webservice/service-deployment.sh webservice/signing/windows/service.jsonnet

clean:
	./mvnw clean