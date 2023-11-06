def latest_maven_release_gav(groupId, artifactId) {
  return sh(
    script: """
      _groupId=${groupId}
      latest_maven_release="\$(curl -sSL "https://repo1.maven.org/maven2/\${_groupId//\\.//}/${artifactId}/maven-metadata.xml" | xml sel -t -v "metadata/versioning/release")"
      echo "${groupId}:${artifactId}:\${latest_maven_release}"
    """,
    returnStdout: true
  ).trim()
}

pipeline {
  agent {
    kubernetes {
      label 'cbi-agent'
      defaultContainer 'cbi'
      yamlMergeStrategy merge()
      yamlFile 'agentPod.yml'
    }
  }

  parameters {
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'The version to be released e.g., 1.3.1')
    string(name: 'NEXT_DEVELOPMENT_VERSION', defaultValue: '', description: 'The next version to be used e.g., 1.3.1-SNAPSHOT')
    booleanParam(name: 'DRY_RUN', defaultValue: false, description: 'Whether the release steps should actually push changes to git and maven repositories, or not.')
  }

  tools {
    jdk 'temurin-jdk17-latest'
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    disableConcurrentBuilds(abortPrevious: true)
  }

  stages {
    stage('Prepare release') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        sh '''
          ./build.sh prepare_release "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"
          ./build.sh check_snapshot_deps "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"
        '''
      }
    }

    stage('Display plugin/dependency updates') {
      steps {
        sh '''
          ./build.sh show_dep_updates "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"
        '''
      }
    }

    stage('Build') {
      steps {
        sh './build.sh build "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"'
        archiveArtifacts 'webservice/**/target/*.jar'
        junit '**/target/surefire-reports/*.xml'
      }
    }

    stage('Push tag to repository') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        sshagent(['github-bot-ssh']) {
            sh './build.sh push_release "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"'
        }
      }
    }

    stage('Deploy') {
      when {
        expression {
          env.BRANCH_NAME == 'main'
        }
      }
      steps {
        sh './build.sh deploy "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"'
      }
    }

    stage('Prepare and push next development cycle') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        sshagent(['github-bot-ssh']) {
            sh './build.sh prepare_next_dev "${RELEASE_VERSION}" "${NEXT_DEVELOPMENT_VERSION}"'
        }
      }
    }
  }
}
