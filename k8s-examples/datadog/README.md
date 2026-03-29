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

The app deployment in `k8s-examples/basic/deployment.yaml` is set up for low-cost dev APM:

- `clusterChecks` disabled
- `orchestratorExplorer` disabled
- `/health` and `/ready` traces sampled at `0%`
- remaining traces sampled at `5%`
- trace rate limited per process

If you want to reduce Datadog APM cost further, prefer Datadog UI Ingestion Controls before adding more aggressive app-side sampling:

- https://docs.datadoghq.com/tracing/guide/trace_ingestion_volume_control/
- https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/java/

Remote Ingestion Control rules configured in the Datadog UI take precedence over local tracer sampling rules.

If `kubectl apply -f k8s-examples/datadog/datadog-agent.yaml` fails with `no matches for kind "DatadogAgent"`, the Datadog Operator CRDs are not installed in the current cluster.
