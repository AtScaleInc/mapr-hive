def buildVersion = ''
def mvnArgs = ''

pipeline {
  agent {
    dockerfile {
      label 'docker'
    }
  }

  stages {
    stage('Checkout') {
      steps{
        checkout scm

        script {
          def shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
          echo "COMMIT: $shortCommit"

          def pom = readMavenPom file: 'pom.xml'
          buildVersion = "${pom.version}.AS${BUILD_ID}-${BRANCH_NAME}"
          echo "BUILD VERSION: ${buildVersion}"

          currentBuild.displayName = "${buildVersion}/${shortCommit}"
        }
      }
    }

    stage('Build') {
      steps {
        sh "mvn versions:set -DnewVersion=${buildVersion}"
        script {
          mvnArgs = "-DskipTests -Phadoop-2 -Pdist"
          if ( "mapr" in BRANCH_NAME ) {
            mvnArgs = mvnArgs + "  -Pprotobuf-250 -Phoneybee"
          }
        }
        sh "mvn clean package ${mvnArgs}"
      }
    }

    stage('Upload to artifactory') {
      steps {
        script {
        def server = Artifactory.server('465784344@1383874667549')

        def uploadSpec = """{
          "files": [
            {
              "pattern": "packaging/target/apache-hive-*-bin.tar.gz",
              "target": "ext-packages/hive/"
            }
          ]
          }"""
        server.upload(uploadSpec)
        }
      }
    }
  }
}
