local deployment = import "../../deployment.libsonnet";

local newDeployment(name, artifactId, version) = deployment.newDeployment(name, artifactId, version) {
  pathspec: error "Must specify a pathspec for a service deployment",
  preDeploy: importstr "../keystore.sh",
};

{
  newDeployment:: newDeployment
}