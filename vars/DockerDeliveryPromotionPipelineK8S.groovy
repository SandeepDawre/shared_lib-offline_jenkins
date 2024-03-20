def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
    agent any
    environment {
        //COMMON 
        TAG      = "${GIT_COMMIT}"

        //FOR DEV
        DEV_DH_URL  = "https://registry.hub.docker.com/teamcloudethix/dev-cdex-jenkins"
        DEV_DH_CREDS = "dev-dockerhub_creds"
        DEV_DH_TAG =  "${env.TAG}"

        //FOR QA
        QA_DH_URL = "https://registry.hub.docker.com/teamcloudethix/qa-cdex-jenkins"
        QA_DH_CREDS = "qa-dockerhub_creds"
        QA_DH_TAG = "${env.TAG}"

        //FOR STAGE
        STAGE_DH_URL =  "https://registry.hub.docker.com/teamcloudethix/stage-cdex-jenkins"
        STAGE_DH_CREDS = "stage-dockerhub_creds"
        STAGE_DH_TAG =  "${env.TAG}"

        //FOR PROD
        PROD_DH_URL =  "https://registry.hub.docker.com/teamcloudethix/prod-cdex-jenkins"
        PROD_DH_CREDS = "prod-dockerhub_creds"
        PROD_DH_TAG = "${env.TAG}"
    }
    
    parameters {
            choice(name: 'account', choices: ['dev', 'qa', 'stage', 'prod'], description: 'Select the environment.')
            string(name: 'commit_id', defaultValue: 'latest', description: 'provide commit id.')
    }
    
    stages {
        stage('Docker Image Build IN Dev') {
            when {
                expression {
                    params.account == 'dev'
                }
            }
            steps {
                echo "Building Docker Image Logging in to Docker Hub & Pushing the Image" 
                
                dockerBuildPush(${env.DEV_DH_URL} , ${env.DEV_DH_CREDS} , ${env.DEV_DH_TAG})

                sh 'echo Image Pushed to DEV'
                sh 'echo Deleting Local docker DEV Image'
            }
        }
        stage('Pull Tag push to QA') {
            when {
                expression {
                    params.account == 'qa'
                }
            }
            steps {
                dockerPullTagPush(${env.DEV_DH_URL} , ${env.DEV_DH_CREDS} , ${env.DEV_DH_TAG} , ${env.QA_DH_URL} , ${env.QA_DH_CREDS} , ${env.QA_DH_TAG})
            }
        }
        stage('Pull Tag push to STAGE') {
            when {
                expression {
                    params.account == 'stage'
                }
            }
            steps {
                dockerPullTagPush(${env.QA_DH_URL} , ${env.QA_DH_CREDS} , ${env.QA_DH_TAG} , ${env.SATGE_DH_URL} , ${env.STAGE_DH_CREDS} , ${env.STAGE_DH_TAG})
            }
        }
        stage('Pull Tag push to PROD') {
            when {
                expression {
                    params.account == 'prod'
                }
            }
            steps {
                dockerPullTagPush(${env.STAGE_DH_URL} , ${env.STAGE_DH_CREDS} , ${env.STAGE_DH_TAG} , ${env.PROD_DH_URL} , ${env.PROD_DH_CREDS} , ${env.PROD_DH_TAG}) 
            }
        }
        stage('DEPLOY TO DEV K8S') {
            when {
                expression {
                    params.account == 'dev'
                }
            }
            steps {
                script {
                    withKubeCredentials(kubectlCredentials: [[ credentialsId: 'dev_kube_config' ]]) {
                        
                        sh 'kubectl get pod --all-namespaces'

                    }
                }
            }
        }
        stage('DEPLOY TO QA K8S') {
            when {
                expression {
                    params.account == 'qa'
                }
            }
            steps {
                script {
                    withKubeCredentials(kubectlCredentials: [[ credentialsId: 'qa_kube_config' ]]) {
                        
                        sh 'kubectl get pod --all-namespaces'

                    }
                }
            }
        }
        stage('DEPLOY TO STAGE K8S') {
            when {
                expression {
                    params.account == 'stage'
                }
            }
            steps {
                script {
                    withKubeCredentials(kubectlCredentials: [[ credentialsId: 'stage_kube_config' ]]) {
                        
                        sh 'kubectl get pod --all-namespaces'

                    }
                }
            }
        }
        stage('DEPLOY TO PROD K8S') {
            when {
                expression {
                    params.account == 'prod'
                }
            }
            steps {
                script {
                    withKubeCredentials(kubectlCredentials: [[ credentialsId: 'prod_kube_config' ]]) {
                        sh 'kubectl get pod --all-namespaces'

                    }
                }
            }
        }

    }
    post { 
        always { 
            echo 'Deleting Project now !! '
            deleteDir()
        }
    }
}

//FOR DOCKER BUILD AND PUSH FOR DEV
def dockerBuildPush (string SRC_DH_URL , string SRC_DH_CREDS , string SRC_DH_TAG ) {
    def app = docker.build('${env.SRC_DH_TAG}')
    docker.withRegistry('${SRC_DH_URL}', '${env.SRC_DH_CREDS}') {
    app.push()
    }
}



//FOR DOCKER PULL TAG PUSH FOR QA STAGE & PROD
def dockerPullTagPush (string SRC_DH_URL , string SRC_DH_CREDS , string SRC_DH_TAG , string DEST_DH_URL , string DEST_DH_CREDS , string DEST_DH_TAG ) {

    //FOR PULL
	docker.withRegistry('${SRC_DH_URL}', '${SRC_DH_CREDS}') {
    docker.image('${SRC_DH_TAG}').pull()
    }
    sh 'echo Image pulled successfully...'

    //FOR TAG
    sh 'echo Taggig Docker image...'
    sh "docker tag ${SRC_DH_TAG}  ${DEST_DH_TAG}" 

    //FOR PUSH
    docker.withRegistry('${DEST_DH_URL}', '${DEST_DH_CREDS}') {
    docker.image('${DEST_DH_TAG}').push()
    }
   
    sh 'echo Image Pushed successfully...'
    sh 'echo Deleting Local docker Images'
    sh "docker image rm ${SRC_DH_TAG}"  
    sh "docker image rm ${DEST_DH_TAG}" 
}

}
