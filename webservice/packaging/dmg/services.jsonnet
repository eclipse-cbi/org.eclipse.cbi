local deployment = import "kube-deployment.libsonnet";

deployment.newDeployment("macos-dmg-packaging", "foundation-codesigning") {}