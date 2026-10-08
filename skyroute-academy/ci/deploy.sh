#!/usr/bin/env bash
# Placeholder deploy step called by the Jenkinsfile. Replace with your platform:
# kubectl set image / helm upgrade / ECS update-service / etc.
set -euo pipefail
env_name="$1"
tag="$2"
echo "Deploying skyroute-api:${tag} to ${env_name}"
# helm upgrade --install skyroute-api ./helm --namespace "skyroute-${env_name}" --set image.tag="${tag}" --wait
