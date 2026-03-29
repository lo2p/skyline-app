# AWS EKS 배포 가이드 (Skyline 항공예약시스템)

본 가이드는 Skyline 데모 애플리케이션을 AWS Elastic Kubernetes Service (EKS) 환경에 배포하는 전체 과정을 안내합니다.

---

## 📋 1. 사전 준비 (Prerequisites)

로컬 환경에 다음 도구들이 설치되어 있어야 합니다.

1.  **AWS CLI**: AWS 리소스 관리를 위한 커맨드 라인 도구
    *   [설치 가이드](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
    *   터미널에서 `aws configure` 명령어로 접근 키(Access Key)와 비밀 키(Secret Key)를 설정합니다.
2.  **eksctl**: EKS 클러스터 생성을 위한 공식 CLI 도구
    *   [설치 가이드](https://eksctl.io/installation/)
3.  **kubectl**: Kubernetes 클러스터 제어 도구
    *   [설치 가이드](https://kubernetes.io/docs/tasks/tools/)
4.  **Docker**: 컨테이너 이미지 빌드 도구
    *   [설치 가이드](https://docs.docker.com/get-docker/)

---

## 🏗️ 2. AWS 인프라 프로비저닝

### 2.1. EKS 클러스터 생성
`eksctl`을 사용하여 EKS 클러스터를 생성합니다. (생성에는 약 15~20분 소요됩니다.)

```bash
eksctl create cluster \
  --name skyline-cluster \
  --region ap-northeast-2 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

*클러스터 생성이 완료되면 `kubectl` 설정(kubeconfig)이 자동으로 업데이트되어 새 클러스터를 가리키게 됩니다.*

*상태 확인:*
```bash
kubectl get nodes
```

### 2.2. MySQL RDS 생성
AWS Console 또는 CLI를 사용하여 RDS 인스턴스를 생성합니다.
(데모 환경이므로 프리티어 적용이 가능한 `db.t3.micro` 권장)

*   **엔진**: MySQL 8.0
*   **보안 그룹 (중요)**: EKS 워커 노드의 보안 그룹(보통 `eks-cluster-sg-...` 혹은 `eks-remoteAccess-...`)에서 포트 `3306` 으로의 인바운드 접근을 허용해야 합니다.
*   데이터베이스 자격 증명(마스터 사용자, 비밀번호)을 기억해 둡니다.

> [!WARNING]
> 초기 데이터(스키마 및 더미 데이터) 주입이 필요합니다.
> 로컬 터미널에서 RDS 엔드포인트로 연결하여 [sql/schema.sql](file:///Users/hwangseung-gi/Antigravity_workspace/skyline_system_demo/sql/schema.sql)과 [sql/seed-data.sql](file:///Users/hwangseung-gi/Antigravity_workspace/skyline_system_demo/sql/seed-data.sql) 스크립트를 실행하여 데이터베이스를 세팅해 주세요.

---

## 🐳 3. 컨테이너 이미지 빌드 및 ECR 푸시

### 3.1. AWS ECR(Elastic Container Registry) 레포지토리 생성
AWS CLI를 사용하여 프라이빗 ECR 레포지토리를 생성합니다.

```bash
aws ecr create-repository \
  --repository-name skyline-app \
  --region ap-northeast-2
```

*(출력 결과에서 `repositoryUri` 값을 메모해 둡니다. 예: `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/skyline-app`)*

### 3.2. ECR 로그인
Docker 클라이언트가 ECR 레지스트리에 푸시할 수 있도록 인증합니다.
(`123456789012.dkr.ecr.ap-northeast-2.amazonaws.com` 부분을 실제 레지스트리 URI로 변경하세요.)

```bash
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com
```

### 3.3. 이미지 빌드 및 푸시
프로젝트 최상위 디렉토리(Dockerfile이 있는 위치)에서 다음을 실행합니다.

```bash
# 1. 빌드 (Mac M1/M2의 경우 --platform linux/amd64 옵션을 추가하여 빌드하는 것을 권장합니다.)
docker build --platform linux/amd64 -t skyline-app:latest .

# 2. 이미지 태깅 (ECR URI 사용)
docker tag skyline-app:latest 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/skyline-app:latest

# 3. ECR로 푸시
docker push 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/skyline-app:latest
```

---

## 🚀 4. Kubernetes 배포 (EKS)

### 4.1. 보안 비밀(Secret) 생성
파드에서 RDS에 접근할 수 있도록 환경변수를 Kubernetes Secret으로 생성합니다. (RDS 접속 정보로 값을 치환하세요.)

```bash
kubectl create secret generic skyline-db-secret \
  --from-literal=DB_HOST="your-rds-endpoint.ap-northeast-2.rds.amazonaws.com" \
  --from-literal=DB_USER="admin" \
  --from-literal=DB_PASSWORD="your-strong-password" \
  --from-literal=DB_NAME="skyline"
```

### 4.2. Deployment 매니페스트 파일 수정
[k8s-examples/basic/deployment.yaml](file:///Users/hwangseung-gi/Antigravity_workspace/skyline_system_demo/k8s-examples/basic/deployment.yaml) 파일을 에디터로 열어서 컨테이너 이미지 경로를 방금 푸시한 **ECR 이미지 URI**로 변경합니다.

```yaml
      containers:
      - name: skyline
        # 아래 부분을 수정하세요!
        image: 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/skyline-app:latest
```

### 4.3. 애플리케이션 배포
수정한 Deployment와 Service 설정을 적용합니다.

```bash
kubectl apply -f k8s-examples/basic/deployment.yaml
kubectl apply -f k8s-examples/basic/service.yaml
```

---

## ✅ 5. 배포 확인 및 접속

```bash
# 파드가 정상적으로 생성되고 Running 상태인지 확인
kubectl get pods

# 생성된 로드밸런서(Service) 주소 확인
kubectl get svc
```

*   `skyline-service`의 `EXTERNAL-IP` 필드에 나타난 주소(예: `a1b2c3d4...ap-northeast-2.elb.amazonaws.com`)를 복사합니다.
*   로드밸런서가 프로비저닝되는 데 약 2~3분 정도 걸릴 수 있습니다.
*   **접속 테스트 (터미널)**: `curl http://<EXTERNAL-IP>:8080/health` (응답으로 `{"status":"UP"}` 등이 오면 성공)
*   React 프론트엔드를 로컬에서 띄워(설정 백엔드 주소 변경 후) 테스트하시거나, 백엔드 API를 직접 콜 해볼 수 있습니다.

---

> [!TIP]
> 실습이 완전히 끝난 후에는 비용이 청구되지 않도록 리소스를 반드시 삭제하세요.
> 1. `eksctl delete cluster --name skyline-cluster --region ap-northeast-2`
> 2. 생성했던 RDS 삭제 (스냅샷 저장 방지)
> 3. ECR 레포지토리 삭제
