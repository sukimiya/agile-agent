pipeline {
	agent any

	options {
		disableConcurrentBuilds()
		buildDiscarder(logRotator(numToKeepStr: '10'))
	}

	stages {
	    stage("build-jar: server and local provider (jdk21)") {
			options { timeout(time: 10, unit: 'MINUTES') }
			steps {
				sh 'mvn -pl agent-server,mock-oauth2 -am clean package -e -U -DskipTests=true'
			}
		}

		stage('build-docker-image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker_registry_credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo '<========= Current git branch $BRANCH_NAME =========>'
                        echo '<============== Build docker image ==============>'
                        echo $DOCKER_PASS | sudo docker login -u ${DOCKER_USER.replace('$', '\\$')} --password-stdin $DOCKER_REGISTRY_URL
                        sudo docker build -t $DOCKER_REGISTRY_URL/agile-agent-server:$BRANCH_NAME-$BUILD_NUMBER -f docker/Dockerfile .
                        sudo docker push $DOCKER_REGISTRY_URL/agile-agent-server:$BRANCH_NAME-$BUILD_NUMBER
                        sudo docker logout $DOCKER_REGISTRY_URL
                    """
                }
            }
        }

    }
}
