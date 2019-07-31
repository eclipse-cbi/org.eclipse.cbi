FROM eclipsecbijenkins/openshift-java:jdk8u212-b04_openj9-0.14.2

RUN apk add --no-cache \
  xmlstarlet

ENTRYPOINT [ "uid_entrypoint", "/bin/bash" ]