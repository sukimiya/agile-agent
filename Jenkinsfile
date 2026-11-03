pipeline {
	agent any

	options {
		disableConcurrentBuilds()
		buildDiscarder(logRotator(numToKeepStr: '10'))
	}

	stages {
	    stage("build-jar: server and local provider (jdk25)") {
			options { timeout(time: 10, unit: 'MINUTES') }
			steps {
				sh 'mvn -pl agent-server,mock-oauth2 -am clean package -e -U -DskipTests=true'
			}
		}

		stage('build-docker-image') {
            steps {
                // Build only: the image stays on the build machine; no registry login or push (2026-11-03).
                // DOCKER_REGISTRY_URL is only the image name prefix; both values default when unset.
                sh '''
                    echo "<========= Current git branch ${BRANCH_NAME:-local} =========>"
                    echo '<============== Build docker image (not pushed) ==============>'
                    sudo docker build -t "${DOCKER_REGISTRY_URL:-agile-agent}/agile-agent-server:${BRANCH_NAME:-local}-${BUILD_NUMBER}" -f docker/Dockerfile .
                '''
            }
        }

    }
}
