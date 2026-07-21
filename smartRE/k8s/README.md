# SmartRE Kenya — Kubernetes Deployment

## Prerequisites
- kubectl configured for your cluster
- Docker images built and pushed to your registry
- Helm installed (for Redis and Kafka)

## Deploy Infrastructure

```bash
# Redis (HA with Sentinel)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install redis bitnami/redis \
  --namespace smartre \
  --set auth.enabled=false \
  --set sentinel.enabled=true \
  --set sentinel.quorum=2 \
  --set replica.replicaCount=3

# Kafka (KRaft mode, no Zookeeper)
helm install kafka bitnami/kafka \
  --namespace smartre \
  --set replicaCount=3 \
  --set kraft.enabled=true \
  --set provisioning.topics[0].name=verification-events \
  --set provisioning.topics[0].partitions=6 \
  --set provisioning.topics[0].replicationFactor=3
```

## Build and Push Images

```bash
# Build all service images
for svc in user-service verification-service property-service viewing-service payment-service review-service api-gateway; do
  docker build -t smartre/$svc:latest ./$svc
  docker push your-registry/smartre/$svc:latest
done
```

## Deploy Services

```bash
# Create namespace and secrets
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml

# Create secrets (edit secret.yaml with real values first)
kubectl apply -f k8s/secret.yaml

# Deploy all services
kubectl apply -f k8s/
```

## Scale Manually

```bash
# Scale property-service to 5 replicas for high traffic
kubectl scale deployment property-service -n smartre --replicas=5

# HPA will auto-scale based on CPU/memory when load increases
kubectl get hpa -n smartre
```

## Monitor

```bash
kubectl get pods -n smartre
kubectl logs -f deployment/property-service -n smartre
kubectl top pods -n smartre
```

## Resource Sizing for 1M+ Users (Production Recommendations)

| Service | Min Replicas | Max (HPA) | Memory | CPU |
|---|---|---|---|---|
| api-gateway | 3 | 20 | 256Mi | 0.5 |
| user-service | 2 | 10 | 512Mi | 1.0 |
| verification-service | 2 | 8 | 512Mi | 1.0 |
| property-service | 3 | 15 | 512Mi | 1.0 |
| viewing-service | 2 | 8 | 256Mi | 0.5 |
| payment-service | 2 | 10 | 512Mi | 1.0 |
| review-service | 2 | 8 | 256Mi | 0.5 |
