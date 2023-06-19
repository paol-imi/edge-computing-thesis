echo building and pushing to local register
cd EdgeDb
./gradlew publish
cd ../../
faas-cli up --skip-deploy --filter session-offloading-manager || exit 1

echo Deploying
./openfaas-offloading-session/deploy.sh || exit 1
echo "Select 'Python tests in sessions_offloading_manager' in the RUN box in the upper-right side corner of intellij"
echo Run the following commands to analyze the logs:
echo ../logs.sh k3d-p3
echo ../logs.sh k3d-p2
echo ../logs.sh k3d-p1
