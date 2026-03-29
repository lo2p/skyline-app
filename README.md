# skyline-app

Skyline is a demo flight reservation application for local Docker and AWS EKS practice.

## Stack

- frontend: React
- backend: Spring Boot
- database: MySQL
- container build: Docker multi-stage build

## Local Run

Build and run the app container:

```bash
docker build -t skyline:latest .

docker run -p 8080:8080 \
  -e DB_HOST=localhost \
  -e DB_PORT=3306 \
  -e DB_NAME=skyline \
  -e DB_USER=skyline_user \
  -e DB_PASSWORD=changeme \
  skyline:latest
```

## Kubernetes

Basic manifests live in `k8s-examples/basic`.

Current Kubernetes flow expects External Secrets instead of a manually created `Secret`:

- `00-namespace.yaml` creates the `skyline` namespace first
- `secret-store.yaml` points to AWS Systems Manager Parameter Store
- `secret.yaml` defines the `ExternalSecret`
- `deployment.yaml` reads DB settings from `skyline-db-secret`
- `service.yaml` creates the application service
- `10-ingress.yaml` creates the public ALB ingress when AWS Load Balancer Controller is installed
- `deployment.yaml` keeps the Datadog log autodiscovery annotation for container log collection

Deploy the basic example:

```bash
kubectl apply -f k8s-examples/basic/
```

Before the first apply, verify External Secrets is actually installed in the same cluster your current `kubectl` context points to:

```bash
kubectl get crd externalsecrets.external-secrets.io secretstores.external-secrets.io
kubectl get deployment -n external-secrets
```

If either command fails, the cluster does not have the External Secrets CRDs yet, even if `skyline-setup-eks.sh` was run. In that case, re-run the setup on the target cluster and confirm it completes without Helm or rollout errors before applying `k8s-examples/basic/`.

If `kubectl get deployment -n external-secrets` returns `No resources found`, the namespace may have been created but the operator itself was not installed successfully.
If you want the ALB from `10-ingress.yaml`, also verify AWS Load Balancer Controller is installed:

```bash
kubectl get deployment -n kube-system aws-load-balancer-controller
```

This flow assumes External Secrets is installed and the following Parameter Store values already exist:

- `/skyline-system-demo/demo/database/host`
- `/skyline-system-demo/demo/database/port`
- `/skyline-system-demo/demo/database/name`
- `/skyline-system-demo/demo/database/username`
- `/skyline-system-demo/demo/database/password`

On the first EKS run, the Spring Boot `production` profile now creates or updates the schema automatically against the Terraform-managed RDS database. If you also want demo data, run `scripts/init-database.sh` after the database is reachable.
For Datadog, log collection in this example relies on the pod annotation in `deployment.yaml`, while APM requires a reachable Datadog Agent on port `8126`.

## Datadog

`k8s-examples/datadog/datadog-agent.yaml` is a `DatadogAgent` custom resource. It is not directly appliable on a fresh cluster by itself. First install the Datadog Operator and its CRDs, then create the Datadog API key secret, and only then apply the `DatadogAgent` manifest.

Example sequence:

```bash
helm repo add datadog https://helm.datadoghq.com
helm repo update
helm install datadog-operator datadog/datadog-operator -n datadog --create-namespace

kubectl create secret generic datadog-secret -n datadog \
  --from-literal api-key=<DATADOG_API_KEY>

kubectl apply -f k8s-examples/datadog/datadog-agent.yaml
```

## Helm Chart

The chart under `k8s-examples/advanced/helm-chart` now also expects an existing Kubernetes secret:

- secret name: `skyline-db-secret`
- keys: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

Database connection pool size is still configured through Helm values.

## API Highlights

- `GET /api/flights`
- `GET /api/flights/{id}`
- `GET /api/flights/search`
- `POST /api/reservations`
- `GET /health`
- `GET /ready`

## Project Layout

- `src/`: Spring Boot backend
- `frontend/`: React frontend
- `sql/`: schema and seed data
- `k8s-examples/`: Kubernetes examples
- `scripts/`: utility scripts
- `docs/`: extra documentation

## Notes

- For local development, you can still pass DB environment variables directly.
- For EKS, the recommended path is Parameter Store plus External Secrets, not `kubectl create secret`.
