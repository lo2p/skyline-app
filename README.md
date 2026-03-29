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

Deploy the basic example:

```bash
kubectl apply -f k8s-examples/basic/
```

This assumes External Secrets is already installed in the cluster and the following Parameter Store values already exist:

- `/skyline-system-demo/demo/database/host`
- `/skyline-system-demo/demo/database/port`
- `/skyline-system-demo/demo/database/name`
- `/skyline-system-demo/demo/database/username`
- `/skyline-system-demo/demo/database/password`

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
