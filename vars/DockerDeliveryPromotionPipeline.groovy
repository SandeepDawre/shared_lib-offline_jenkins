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
    stages {
        stage('Docker Image Build IN Dev') {
            when {
                branch 'dev'
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
                branch 'qa'
            }
            steps {
                dockerPullTagPush(${env.DEV_DH_URL} , ${env.DEV_DH_CREDS} , ${env.DEV_DH_TAG} , ${env.QA_DH_URL} , ${env.QA_DH_CREDS} , ${env.QA_DH_TAG})
            }
        }
        stage('Pull Tag push to STAGE') {
            when {
                branch 'stage'
            }
            steps {
                dockerPullTagPush(${env.QA_DH_URL} , ${env.QA_DH_CREDS} , ${env.QA_DH_TAG} , ${env.SATGE_DH_URL} , ${env.STAGE_DH_CREDS} , ${env.STAGE_DH_TAG})
            }
        }
        stage('Pull Tag push to PROD') {
            when {
                branch 'prod'
            }
            steps {
                dockerPullTagPush(${env.STAGE_DH_URL} , ${env.STAGE_DH_CREDS} , ${env.STAGE_DH_TAG} , ${env.PROD_DH_URL} , ${env.PROD_DH_CREDS} , ${env.PROD_DH_TAG}) 
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



//FOR DOCKER PULL TAG PUSH FOR QA STAGE & PROD
def dockerPullTagPush (string SRC_DH_URL , string SRC_DH_CREDS , string SRC_DH_TAG , string DEST_DH_URL , string DEST_DH_CREDS , string DEST_DH_TAG ) {

    //FOR PULL
	docker.withRegistry('${SRC_DH_URL}', '${SRC_DH_CREDS}') {
    docker.image('${SRC_DH_TAG}').pull()
    sh 'echo Image pulled successfully...'

    //FOR TAG
    sh 'echo Taggig Docker image...'
    sh "docker tag ${SRC_DH_TAG}  ${DEST_DH_TAG}" 

    //FOR PUSH
    docker.withRegistry('${DEST_DH_URL}', '${DEST_DH_CREDS}') {
    docker.image('${DEST_DH_TAG}').push()

    sh 'echo Image Pushed successfully...'
    sh 'echo Deleting Local docker Images'
    sh "docker image rm ${SRC_DH_TAG}"  
    sh "docker image rm ${DEST_DH_TAG}" 
}

}
