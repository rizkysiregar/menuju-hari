pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        // Paksa legacy builder: plugin buildx tidak selalu terpasang di
        // container Jenkins; Dockerfile dirancang kompatibel legacy.
        DOCKER_BUILDKIT = '0'
        GITOPS_REPOSITORY = 'https://github.com/rizkysiregar/homelabs-gitops.git'
        GITOPS_BRANCH = 'main'
        GITOPS_MANIFEST = 'apps/menujuhari/kustomization.yaml'
        YQ_IMAGE = 'mikefarah/yq:4.45.4'
        MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-25'
        IMAGE_PLATFORM = 'linux/amd64'
        // Nama image per service = rizkysiregar/menujuhari-<service>
        IMAGE_PREFIX = 'rizkysiregar/menujuhari'
        BACKEND_SERVICES = 'identity,invitation,catalog,media,order,payment'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Preparing build ${IMAGE_TAG}"
                }
            }
        }

        stage('Determine Changed Services') {
            steps {
                script {
                    def baseCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'
                    def changedFiles = sh(
                        script: "git diff --name-only ${baseCommit}..HEAD || echo '__ALL__'",
                        returnStdout: true
                    ).trim().split('\n') as List

                    def all = env.BACKEND_SERVICES.split(',') as List

                    // Perubahan berikut memicu rebuild SEMUA service:
                    // root pom, pipeline, .dockerignore, Dockerfile generik,
                    // dan library bersama (security-starter).
                    def touchesAll = changedFiles.any {
                        it == 'pom.xml' || it == 'Jenkinsfile' ||
                        it == '.dockerignore' || it == 'services/Dockerfile' ||
                        it.startsWith('services/shared/')
                    }

                    if (changedFiles.contains('__ALL__') || touchesAll) {
                        env.CHANGED_SERVICES = all.join(',')
                    } else {
                        env.CHANGED_SERVICES = all.findAll { svc ->
                            changedFiles.any { it.startsWith("services/${svc}/") }
                        }.join(',')
                    }

                    if (env.CHANGED_SERVICES?.trim()) {
                        echo "Services to build: ${env.CHANGED_SERVICES}"
                    } else {
                        echo 'No backend service changed; skipping build.'
                    }
                }
            }
        }

        stage('Test') {
            when {
                expression { env.CHANGED_SERVICES?.trim() }
            }
            steps {
                // --network=host: Testcontainers membaca mapped port di host docker;
                // socket di-mount agar Testcontainers bisa menyala; group ditambahkan
                // agar user Jenkins berhak membaca /var/run/docker.sock.
                sh '''
                    docker run --rm \\
                      --network=host \\
                      --user "$(id -u):$(id -g)" \\
                      --group-add "$(stat -c %g /var/run/docker.sock 2>/dev/null || echo 999)" \\
                      --volumes-from "${JENKINS_CONTAINER_ID:-$HOSTNAME}" \\
                      --workdir "$WORKSPACE" \\
                      "$MAVEN_IMAGE" \\
                      mvn -B -Dmaven.repo.local=/tmp/.m2 clean test
                '''
            }
        }

        stage('Build and Push Images') {
            when {
                expression { env.CHANGED_SERVICES?.trim() }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        printf '%s' "$DOCKER_PASSWORD" | docker login docker.io \\
                          --username "$DOCKER_USERNAME" \\
                          --password-stdin
                        set -x

                        for SVC in $(echo "$CHANGED_SERVICES" | tr ',' ' '); do
                            docker build \\
                              --platform "$IMAGE_PLATFORM" \\
                              --build-arg SERVICE="$SVC" \\
                              -f services/Dockerfile \\
                              --tag "$IMAGE_PREFIX-$SVC:$IMAGE_TAG" \\
                              .
                            docker push "$IMAGE_PREFIX-$SVC:$IMAGE_TAG"
                        done

                        docker logout docker.io || true
                    '''
                }
            }
        }

        stage('Update GitOps Image Tags') {
            when {
                expression { env.CHANGED_SERVICES?.trim() }
            }
            steps {
                dir('gitops') {
                    deleteDir()
                    git branch: "${GITOPS_BRANCH}",
                        credentialsId: 'github-all-repo-pat',
                        url: "${GITOPS_REPOSITORY}"

                    withCredentials([
                        gitUsernamePassword(
                            credentialsId: 'github-all-repo-pat',
                            gitToolName: 'Default'
                        )
                    ]) {
                        sh '''
                            export SERVICE_NAME
                            for SVC in $(echo "$CHANGED_SERVICES" | tr ',' ' '); do
                                export SERVICE_NAME="$IMAGE_PREFIX-$SVC"
                                docker run --rm \\
                                  --user "$(id -u):$(id -g)" \\
                                  --volumes-from "${JENKINS_CONTAINER_ID:-$HOSTNAME}" \\
                                  --workdir "$PWD" \\
                                  --env SERVICE_NAME \\
                                  --env IMAGE_TAG \\
                                  "$YQ_IMAGE" \\
                                  -i \\
                                  '(.images[] | select(.name == strenv(SERVICE_NAME))).newTag = strenv(IMAGE_TAG)' \\
                                  "$GITOPS_MANIFEST"
                            done

                            git diff --check
                            git config user.name 'Jenkins'
                            git config user.email 'jenkins@menujuhari.ci'
                            git add "$GITOPS_MANIFEST"

                            if git diff --cached --quiet; then
                                echo "GitOps already points to $IMAGE_TAG; nothing to commit."
                                exit 0
                            fi

                            git commit -m "deploy(menujuhari): ${IMAGE_TAG} [${CHANGED_SERVICES}]"
                            git push origin "HEAD:$GITOPS_BRANCH"
                        '''
                    }
                }
            }
        }
    }
}
