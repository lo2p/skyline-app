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
  -e DB_NAME=skylineapp \
  -e DB_USER=skyline_user \
  -e DB_PASSWORD=changeme \
  skyline:latest
```

## EKS Deploy

This repo is designed to be deployed after the infrastructure in `skyline-infra-terraform` has already created:

- the EKS cluster
- the RDS MySQL instance
- the SSM Parameter Store values for the database
- the External Secrets IAM permissions
- the AWS Load Balancer Controller

The application manifests under `k8s-examples/basic/` now include:

- `00-namespace.yaml`: creates the `skyline` namespace
- `secret-store.yaml`: `SecretStore` for AWS Systems Manager Parameter Store
- `secret.yaml`: `ExternalSecret` that creates `skyline-db-secret`
- `deployment.yaml`: app deployment, probes, and Datadog tags/annotations
- `service.yaml`: internal `ClusterIP` service
- `10-ingress.yaml`: public ALB ingress

### 1. Verify Cluster Prerequisites

Check the current context and required controllers first:

```bash
kubectl config current-context
kubectl get crd externalsecrets.external-secrets.io secretstores.external-secrets.io
kubectl get deployment -n external-secrets
kubectl get deployment -n kube-system aws-load-balancer-controller
```

If External Secrets or the AWS Load Balancer Controller is missing, re-run the EKS bootstrap from the infra repo before deploying the app.

### 2. Verify Database Parameters Exist

The app expects these SSM Parameter Store keys to already exist:

- `/skyline-system-demo/demo/database/host`
- `/skyline-system-demo/demo/database/port`
- `/skyline-system-demo/demo/database/name`
- `/skyline-system-demo/demo/database/username`
- `/skyline-system-demo/demo/database/password`

### 3. Build and Push the App Image

Update the image tag in `k8s-examples/basic/deployment.yaml` after pushing a new image.

Example:

```bash
docker build -t 438916407893.dkr.ecr.ap-northeast-2.amazonaws.com/skyline:test4 .
docker push 438916407893.dkr.ecr.ap-northeast-2.amazonaws.com/skyline:test4
```

### 4. Deploy the Basic Manifests

Apply everything in one shot:

```bash
kubectl apply -f k8s-examples/basic/
kubectl rollout status deployment/skyline-app -n skyline
```

### 5. Verify Secrets, Pods, Service, and ALB

```bash
kubectl get externalsecret,secretstore -n skyline
kubectl get secret skyline-db-secret -n skyline
kubectl get pods -n skyline
kubectl get svc -n skyline
kubectl get ingress -n skyline
kubectl describe ingress skyline-ingress -n skyline
kubectl get ingress skyline-ingress -n skyline -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'; echo
```

The service is intentionally `ClusterIP`. Public access comes from the ALB created by `10-ingress.yaml`, not from a `LoadBalancer` service.

### 6. Initialize Demo Data If Needed

The `production` profile now uses Hibernate schema auto-update, so the app can create/update tables on first startup against a fresh Terraform-created database.

If you also want the sample data:

```bash
./scripts/init-database.sh <rds-endpoint> <db-user> <db-password> skylineapp
```

The schema script is now idempotent for trigger creation, so rerunning it is safe.

### 7. Test the App

```bash
ALB=$(kubectl get ingress skyline-ingress -n skyline -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
curl -i "http://$ALB/health"
curl -i "http://$ALB/ready"
curl -i "http://$ALB/api/flights"
```

## Datadog

`k8s-examples/datadog/datadog-agent.yaml` is a `DatadogAgent` custom resource for the Datadog Operator. It does not work on a fresh cluster by itself. The correct order is:

1. install the Datadog Operator
2. create the `datadog-secret` API key secret in the `datadog` namespace
3. apply the `DatadogAgent` custom resource
4. restart the app deployment so pods are recreated with Datadog injection

Example sequence:

```bash
helm repo add datadog https://helm.datadoghq.com
helm repo update
helm install datadog-operator datadog/datadog-operator -n datadog --create-namespace

kubectl create secret generic datadog-secret -n datadog \
  --from-literal api-key='YOUR_REAL_DATADOG_API_KEY'

kubectl apply -f k8s-examples/datadog/datadog-agent.yaml

kubectl rollout restart deployment/skyline-app -n skyline
kubectl rollout status deployment/skyline-app -n skyline
```

Verify Datadog:

```bash
kubectl get datadogagent -n datadog
kubectl get pods -n datadog
AGENT_POD=$(kubectl get pods -n datadog -o name | grep datadog-agent | head -n 1)
kubectl exec -n datadog "$AGENT_POD" -- agent status
```

What to look for:

- `API key valid`
- APM traces received by the agent
- logs being sent successfully
- `service: skyline-app`
- `env: demo`

If `kubectl apply -f k8s-examples/datadog/datadog-agent.yaml` fails with `no matches for kind "DatadogAgent"`, the Operator CRDs are not installed in the current cluster.

## Helm Chart

The chart under `k8s-examples/advanced/helm-chart` expects an existing Kubernetes secret:

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
- The default database name is `skylineapp`.
- The Datadog deployment uses the service name `skyline-app` consistently across logs, tags, and traces.
