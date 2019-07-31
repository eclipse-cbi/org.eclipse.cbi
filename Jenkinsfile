pipeline {
  agent {
    kubernetes {
      label 'cbi-agent'
      yamlFile 'agentPod.yml'
    }
  }

  parameters { 
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'The version to be released e.g., 1.3.1') 
    string(name: 'NEXT_DEVELOPMENT_VERSION', defaultValue: '', description: 'The next version to be used e.g., 1.3.1-SNAPSHOT') 
    booleanParam(name: 'DRY_RUN', defaultValue: false, description: 'Whether the release step should push changes to git repo and maven repo')
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  triggers { pollSCM('@daily') }

  stages {
    stage('Prepare release') {
      when { 
        expression {
          env.RELEASE_VERSION != '' && env.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        container('cbi') {
          sh '''
            ./build.sh prepare_release pom.xml
            ./build.sh check_snapshot_deps pom.xml
          '''
        }
      }
    }

    stage('Build') {
      steps {
        container('cbi') {
          sh './build.sh build pom.xml'
        }
      }
    }

    stage('Deploy') {
      steps {
        container('cbi') {
          sh './build.sh deploy pom.xml'
        }
      }
    }

    stage('Tag and push repo') {
      when { 
        expression {
          env.RELEASE_VERSION != '' && env.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        container('cbi') {
          sshagent(['git.eclipse.org-bot-ssh']) {
            sh './build.sh push_release pom.xml'
          }
        }
      }
    }

    stage('Prepare next development cycle') {
      when { 
        expression {
          env.RELEASE_VERSION != '' && env.NEXT_DEVELOPMENT_VERSION != ''
        }
      }
      steps {
        container('cbi') {
          sshagent(['git.eclipse.org-bot-ssh']) {
            sh './build.sh prepare_next_dev pom.xml'
          }
        }
      }
    }
  }
}
