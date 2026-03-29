`datadog-agent.yaml` defines a `DatadogAgent` custom resource for the Datadog Operator.

Prerequisites:

- Datadog Operator installed in the cluster
- Datadog Operator CRDs installed
- a `datadog-secret` secret with your Datadog API key

Example install flow:

```bash
helm repo add datadog https://helm.datadoghq.com
helm repo update
helm install datadog-operator datadog/datadog-operator -n datadog --create-namespace

kubectl create secret generic datadog-secret -n default \
  --from-literal api-key=<DATADOG_API_KEY>

kubectl apply -f k8s-examples/datadog/datadog-agent.yaml
kubectl get datadogagent
kubectl get pods -A | grep datadog
```

If `kubectl apply -f k8s-examples/datadog/datadog-agent.yaml` fails with `no matches for kind "DatadogAgent"`, the Datadog Operator CRDs are not installed in the current cluster.
