#!/usr/bin/env bash
set -e

# Create the container the first time; reuse it afterwards
if ! docker ps -a --format '{{.Names}}' | grep -q '^sonarqube$'; then
  docker run -d --name sonarqube \
    -p 9000:9000 \
    -v sonarqube_data:/opt/sonarqube/data \
    -v sonarqube_extensions:/opt/sonarqube/extensions \
    -v sonarqube_logs:/opt/sonarqube/logs \
    sonarqube:community
else
  docker start sonarqube
fi

# Wait until it's actually ready
echo "Waiting for SonarQube..."
until curl -s http://localhost:9000/api/system/status | grep -q '"status":"UP"'; do
  sleep 5
done

# Run the scan, blocking on the quality gate
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=my-app \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.qualitygate.wait=true

# Shut it down (graceful)
docker stop sonarqube