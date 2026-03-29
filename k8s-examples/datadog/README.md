`datadog-agent.yaml` defines a `DatadogAgent` custom resource for the Datadog Operator.

Prerequisites:

- Datadog Operator installed in the cluster
- Datadog Operator CRDs installed
- a valid `datadog-secret` in the `datadog` namespace

Install flow:

```bash
helm repo add datadog https://helm.datadoghq.com
helm repo update
helm install datadog-operator datadog/datadog-operator -n datadog --create-namespace

kubectl create secret generic datadog-secret -n datadog \
  --from-literal api-key='YOUR_REAL_DATADOG_API_KEY'

kubectl apply -f k8s-examples/datadog/datadog-agent.yaml
kubectl get datadogagent -n datadog
kubectl get pods -n datadog
```

After the Agent is running, restart the application deployment so Datadog admission injection is applied to new pods:

```bash
kubectl rollout restart deployment/skyline-app -n skyline
kubectl rollout status deployment/skyline-app -n skyline
```

Verify end-to-end status:

```bash
AGENT_POD=$(kubectl get pods -n datadog -o name | grep datadog-agent | head -n 1)
kubectl exec -n datadog "$AGENT_POD" -- agent status
```

What to look for in `agent status`:

- `API key valid`
- APM `Traces received`
- log sending over HTTPS
- no `403` or `API Key invalid`

If `kubectl apply -f k8s-examples/datadog/datadog-agent.yaml` fails with `no matches for kind "DatadogAgent"`, the Datadog Operator CRDs are not installed in the current cluster.
