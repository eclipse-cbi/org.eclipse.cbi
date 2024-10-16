local deployment = import "kube-deployment.libsonnet";

deployment.newDeployment("macos-codesign", "foundation-codesigning") {}
