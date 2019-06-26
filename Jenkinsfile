pipeline {
  agent any

  tools { 
    maven 'apache-maven-latest' 
    jdk 'adoptopenjdk-hotspot-jdk8-latest' 
  }


  stages {
    stage('Build') {
      steps {
        sh '''
          mvn clean verify
        '''
      }
    }
    stage('Deploy') {
      steps {
        sh '''
          mvn deploy
        '''
      }
    }
  }
}
