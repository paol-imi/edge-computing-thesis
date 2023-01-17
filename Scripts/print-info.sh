
if [ $# -eq 0 ]
then
	echo One argument needed: profilename
	exit 1
fi

kubectl config use-context $1
printf "\n"

echo Entry point:
echo http://$(kubectl get nodes -o jsonpath="{.items[0].status.addresses[0].address}"):31112
printf "\n"

echo OpenFaaS password:
kubectl get secret --namespace openfaas basic-auth -o jsonpath="{.data.basic-auth-password}" | base64 --decode
printf "\n"

echo Redis password:
kubectl get secret --namespace openfaas-fn my-openfaas-redis -o jsonpath="{.data.redis-password}" | base64 --decode
printf "\n"