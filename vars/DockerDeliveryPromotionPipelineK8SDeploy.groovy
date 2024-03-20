def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
    agent any
    environment {
        //COMMON 
        ACCOUNT         =       "${params.account}"
        COMMITID        =       "${GIT_COMMIT}"


        //FOR DEV
        DEV_DH_URL      =       "registry.hub.docker.com/teamcloudethix/dev-cdex-jenkins"
        DEV_DH_CREDS    =       "dev-dockerhub_creds"
        DEV_DH_TAG      =       "${env.DEV_DH_URL}" + ":" + "${env.COMMITID}"
        DEV_CONFIG      =       "dev_kube_config"
        

        //FOR QA
        QA_DH_URL       =       "registry.hub.docker.com/teamcloudethix/qa-cdex-jenkins"
        QA_DH_CREDS     =       "qa-dockerhub_creds"
        QA_DH_TAG       =       "${env.QA_DH_URL}" + ":" + "${env.COMMITID}"
        QA_CONFIG       =       "qa_kube_config"

        //FOR STAGE
        STAGE_DH_URL    =       "registry.hub.docker.com/teamcloudethix/stage-cdex-jenkins"
        STAGE_DH_CREDS  =       "stage-dockerhub_creds"
        STAGE_DH_TAG    =       "${env.STAGE_DH_URL}" + ":" + "${env.COMMITID}"
        STAGE_CONFIG    =       "stage_kube_config"

        //FOR PROD
        PROD_DH_URL     =       "registry.hub.docker.com/teamcloudethix/prod-cdex-jenkins"
        PROD_DH_CREDS   =       "prod-dockerhub_creds"
        PROD_DH_TAG     =       "${env.PROD_DH_URL}" + ":" + "${env.COMMITID}"
        PROD_CONFIG     =       "prod_kube_config"
    }
    
    parameters {
            choice(name: 'account', choices: ['dev', 'qa', 'stage', 'prod'], description: 'Select the environment.')
            string(name: 'commit_id', defaultValue: 'latest', description: 'provide commit id.')
    }
    
    stages {
        stage('DOCKER IMAGE BUILD IN DEV') {
            when {
                expression {
                    params.account == 'dev'
                }
            }
            steps {
                echo "Building Docker Image Logging in to Docker Hub & Pushing the Image" 
                
                dockerBuildPush(env.DEV_DH_URL , env.DEV_DH_CREDS , env.DEV_DH_TAG)

                sh 'echo Image Pushed to DEV'
                sh 'echo Deleting Local docker DEV Image'
            }
        }
        stage('PULL TAG PUSH TO QA') {
            when {
                expression {
                    params.account == 'qa'
                }
            }
            steps {
                dockerPullTagPush(env.DEV_DH_URL , env.DEV_DH_CREDS , env.DEV_DH_TAG , env.QA_DH_URL , env.QA_DH_CREDS , env.QA_DH_TAG)
            }
        }
        stage('PULL TAG PUSH TO STAGE') {
            when {
                expression {
                    params.account == 'stage'
                }
            }
            steps {
                dockerPullTagPush(env.QA_DH_URL , env.QA_DH_CREDS , env.QA_DH_TAG , env.STAGE_DH_URL , env.STAGE_DH_CREDS , env.STAGE_DH_TAG)
            }
        }
        stage('PULL TAG PUSH TO PROD') {
            when {
                expression {
                    params.account == 'prod'
                }
            }
            steps {
                dockerPullTagPush(env.STAGE_DH_URL , env.STAGE_DH_CREDS , env.STAGE_DH_TAG , env.PROD_DH_URL , env.PROD_DH_CREDS , env.PROD_DH_TAG) 
            }
        }
        stage('DEPLOY TO K8S DEV') {
            when {
                expression {
                    params.account == 'dev'
                }
            }
            steps {

                deployOnK8s(env.DEV_CONFIG , env.ACCOUNT , env.COMMITID)

            }
        }
        stage('DEPLOY TO K8S QA') {
            when {
                expression {
                    params.account == 'qa'
                }
            }
            steps {

                deployOnK8s(env.QA_CONFIG , env.ACCOUNT , env.COMMITID)

            }
        }
        stage('DEPLOY TO K8S STAGE') {
            when {
                expression {
                    params.account == 'stage'
                }
            }
            steps {

                deployOnK8s(env.STAGE_CONFIG , env.ACCOUNT , env.COMMITID)

            }
        }
        stage('DEPLOY TO K8S PROD') {
            when {
                expression {
                    params.account == 'prod'
                }
            }
            steps {

                deployOnK8s(env.PROD_CONFIG , env.ACCOUNT , env.COMMITID)

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

}
//FOR DOCKER BUILD AND PUSH FOR DEV
def dockerBuildPush( String SRC_DH_URL , String SRC_DH_CREDS , String SRC_DH_TAG ) {
    def app = docker.build(SRC_DH_TAG)
    docker.withRegistry("https://" + SRC_DH_URL , SRC_DH_CREDS) {
    app.push()
    }
}



//FOR DOCKER PULL TAG PUSH FOR QA STAGE & PROD
def dockerPullTagPush( String SRC_DH_URL , String SRC_DH_CREDS , String SRC_DH_TAG , String DEST_DH_URL , String DEST_DH_CREDS , String DEST_DH_TAG ) {

    //FOR PULL
	docker.withRegistry("https://" + SRC_DH_URL , SRC_DH_CREDS) {
    docker.image(SRC_DH_TAG).pull()
    }
    sh 'echo Image pulled successfully...'

    //FOR TAG
    sh 'echo Taggig Docker image...'
    sh "docker tag ${SRC_DH_TAG} ${DEST_DH_TAG}" 

    //FOR PUSH
    docker.withRegistry("https://" + DEST_DH_URL , DEST_DH_CREDS) {
    docker.image(DEST_DH_TAG).push()
    }
   
    sh 'echo Image Pushed successfully...'
    sh 'echo Deleting Local docker Images'
    sh "docker image rm ${SRC_DH_TAG}"  
    sh "docker image rm ${DEST_DH_TAG}" 
}


//DEPLOY ON K8S

def deployOnK8s(String KUBE_CONFIG, String ACCOUNT, String COMMIT) {

    withKubeConfig(credentialsId: "${KUBE_CONFIG}", restrictKubeConfigAccess: true) {

        sh 'echo Deploying application on ${ACCOUNT} K8S cluster'
        sh 'echo Replacing K8S manifests files with sed....'
        sh "sed -i -e 's/{{ACCOUNT}}/${ACCOUNT}/g' -e 's/{{COMMITID}}/${COMMIT}/g' KUBE/*"
        sh 'echo K8S manifests files after replace with sed ...'
        sh 'cat KUBE/deployment.yaml'
        sh 'kubectl apply -f KUBE/.'
	    
    }
	
}
