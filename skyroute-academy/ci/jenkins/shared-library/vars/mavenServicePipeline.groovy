// Jenkins shared library step (vars/ = global steps). Lets 50 microservices share ONE
// pipeline definition; each repo's Jenkinsfile becomes:
//
//   @Library('skyroute-pipelines') _
//   mavenServicePipeline(service: 'skyroute-api', javaVersion: '17', runE2e: true)
//
def call(Map config = [:]) {
    def service = config.service ?: error('service is required')
    def runE2e = config.get('runE2e', false)

    pipeline {
        agent { label "jdk${config.javaVersion ?: '17'}" }
        options { timestamps(); timeout(time: 30, unit: 'MINUTES') }
        stages {
            stage('Build & test') {
                steps { sh 'mvn -B -ntp clean verify' }
                post { always { junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml' } }
            }
            stage('Sonar') {
                steps {
                    withSonarQubeEnv('sonarqube') { sh "mvn -B -ntp sonar:sonar -Dsonar.projectKey=${service}" }
                }
            }
            stage('Quality gate') {
                steps { timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } }
            }
            stage('E2E') {
                when { expression { runE2e } }
                steps { dir('frontend') { sh 'npm ci && CI=true npm run test:e2e' } }
            }
        }
    }
}
