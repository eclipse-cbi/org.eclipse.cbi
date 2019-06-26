pipeline {
  agent any

  tools { 
    maven 'apache-maven-latest' 
    jdk 'adoptopenjdk-hotspot-jdk8-latest' 
  }

  parameters { 
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'The version to be released e.g., 1.3.1') 
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '', description: 'The next version to be used e.g., 1.3.1-SNAPSHOT') 
    booleanParam(name: 'DRY_RUN', defaultValue: false, description: 'Wether the release step should push changes to git repo and maven repo')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  triggers { pollSCM('@daily') }

  stages {
    stage('Build') {
      steps {
        sh '''
          mvn -B -e clean verify
        '''
      }
    }
    stage('Deploy') {
      steps {
        sh '''
          mvn  -B -e deploy
        '''
      }
    }
    stage('Release') {
      when { 
        expression {
          env.RELEASE_VERSION != '' && env.DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        sshagent(['git.eclipse.org-bot-ssh']) {
          sh '''
            git config --global user.email "cbi-bot@eclipse.org"
            git config --global user.name "CBI Bot"
            ./release.sh pom.xml
          '''
        }
      }
    }
  }
}
