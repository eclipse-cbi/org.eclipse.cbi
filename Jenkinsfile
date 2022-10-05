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

  environment {
    POM='pom.xml'
    MAVEN_OPTS='-Xmx1024m -Xms256m -XshowSettings:vm -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'
    VERSIONS_MAVEN_PLUGIN = latest_maven_release_gav('org.codehaus.mojo', 'versions-maven-plugin')
    MAVEN_DEPENDENCY_PLUGIN = latest_maven_release_gav('org.apache.maven.plugins', 'maven-dependency-plugin')
    ARTIFACT_ID = sh(
      script: "xml sel -N mvn=\"http://maven.apache.org/POM/4.0.0\" -t -v  \"/mvn:project/mvn:artifactId\" \"${env.POM}\"",
      returnStdout: true
    )
    GROUP_ID = sh(
      script: "xml sel -N mvn=\"http://maven.apache.org/POM/4.0.0\" -t -v  \"(/mvn:project/mvn:groupId|/mvn:project/mvn:parent/mvn:groupId)[last()]\" \"${env.POM}\"",
      returnStdout: true
    )
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
          "${WORKSPACE}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:set" -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false -f "${POM}"

          git config user.email "cbi-bot@eclipse.org"
          git config user.name "CBI Bot"
          git config --local credential.helper "!f() { echo username=\\$GIT_AUTH_USR; echo password=\\$GIT_AUTH_PSW; }; f"

          git add --all
          git commit -m "Prepare release ${RELEASE_VERSION}"
          git tag "v${RELEASE_VERSION}" -m "Release ${RELEASE_VERSION}"

          # quick check that we don't depend on SNAPSHOT anymore
          if "${WORKSPACE}/mvnw" "${MAVEN_DEPENDENCY_PLUGIN}:list" -f "${POM}" | grep SNAPSHOT; then
            >&2 echo "ERROR: At least one dependency to a 'SNAPSHOT' version has been found from '${POM}'"
            >&2 echo "ERROR: It is forbidden for releasing"
            exit 1
          fi

          if grep SNAPSHOT "${POM}"; then
            >&2 echo "ERROR: At least one 'SNAPSHOT' string has been found in '${POM}'"
            >&2 echo "ERROR: It is forbidden for releasing"
            exit 1
          fi
        '''
      }
    }

    stage('Display plugin/dependency updates') {
      steps {
        sh '''
          "${WORKSPACE}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:display-plugin-updates" -f "${POM}"
          "${WORKSPACE}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:display-dependency-updates" -f "${POM}"
        '''
      }
    }

    stage('Build') {
      steps {
        sh '"${WORKSPACE}/mvnw" clean verify -f "${POM}"'
        archiveArtifacts 'webservice/**/target/*.jar'
        junit '**/target/surefire-reports/*.xml'
      }
    }

    stage('Deploy') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && env.DRY_RUN != 'true'
        }
      }
      steps {
        sh '''
          "${WORKSPACE}/mvnw" deploy -f "${POM}"
        '''
      }
    }

    stage('Push tag to repository') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != '' && env.DRY_RUN != 'true'
        }
      }
      environment {
        GIT_AUTH = credentials('github-bot')
      }
      steps {
        sh'''
          git push origin "v${RELEASE_VERSION}"
        '''
      }
    }

    stage('Prepare next development cycle') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      environment {
        GIT_AUTH = credentials('github-bot')
      }
      steps {
        sh '''
          # clean and prepare for next iteration
          git clean -q -x -d -ff
          git reset -q --hard HEAD

          "${WORKSPACE}/mvnw" "${VERSIONS_MAVEN_PLUGIN}:set" -DnewVersion="${NEXT_DEVELOPMENT_VERSION}" -DgenerateBackupPoms=false -f "${POM}"

          # commit next iteration changes
          git add --all
          git commit -m "Prepare for next development iteration (${NEXT_DEVELOPMENT_VERSION})"
        '''
      }
    }

    stage('Push next development cycle') {
      when {
        expression {
          env.BRANCH_NAME == 'main' && params.RELEASE_VERSION != '' && params.NEXT_DEVELOPMENT_VERSION != '' && env.DRY_RUN != 'true'
        }
      }
      environment {
        GIT_AUTH = credentials('github-bot')
      }
      steps {
        sh '''
          git push origin "${GIT_BRANCH}"
        '''
      }
    }
  }
}
