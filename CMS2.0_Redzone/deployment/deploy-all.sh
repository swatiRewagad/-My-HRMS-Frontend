#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENSHIFT_DIR="$SCRIPT_DIR/openshift"

echo "============================================="
echo "  CMS 2.0 - OpenShift Deployment Script"
echo "  Namespace: rbi-cms"
echo "============================================="

# Check oc login
if ! oc whoami &>/dev/null; then
  echo "ERROR: Not logged in to OpenShift. Run 'oc login' first."
  exit 1
fi

echo ""
echo "[Phase 1] Namespace & Foundation"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/namespace.yaml"
echo "  ✓ Namespace rbi-cms"

echo ""
echo "[Phase 2] ConfigMaps & Secrets"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/configmap-common.yaml"
echo "  ✓ ConfigMap: cms-common-config"

oc apply -f "$OPENSHIFT_DIR/secret-db.yaml"
echo "  ✓ Secret: cms-db-secret"

oc apply -f "$OPENSHIFT_DIR/secret-keycloak.yaml"
echo "  ✓ Secret: cms-keycloak-secret"

oc apply -f "$OPENSHIFT_DIR/secret-sms.yaml"
echo "  ✓ Secret: cms-sms-secret"

oc apply -f "$OPENSHIFT_DIR/secret-opensearch.yaml"
echo "  ✓ Secret: cms-opensearch-secret"

oc apply -f "$OPENSHIFT_DIR/secret-kafka.yaml"
echo "  ✓ Secret: cms-kafka-secret"

echo ""
echo "[Phase 3] Persistent Volume Claims"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/pvc.yaml"
echo "  ✓ PVC: cms-attachments-pvc"
echo "  ✓ PVC: cms-storage-pvc"

echo ""
echo "[Phase 4] Core Services (no inter-service deps)"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/deployment-audit-service.yaml"
echo "  ✓ cms-audit-service"

oc apply -f "$OPENSHIFT_DIR/deployment-storage-service.yaml"
echo "  ✓ cms-storage-service"

oc apply -f "$OPENSHIFT_DIR/deployment-paddle-ocr.yaml"
echo "  ✓ cms-paddle-ocr"

oc apply -f "$OPENSHIFT_DIR/deployment-notification-service.yaml"
echo "  ✓ cms-notification-service"

oc apply -f "$OPENSHIFT_DIR/deployment-search-service.yaml"
echo "  ✓ cms-search-service"

echo ""
echo "[Phase 5] Business Logic Services"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/deployment-rules-service.yaml"
echo "  ✓ cms-rules-service"

oc apply -f "$OPENSHIFT_DIR/deployment-eligibility-service.yaml"
echo "  ✓ cms-eligibility-service"

oc apply -f "$OPENSHIFT_DIR/deployment-sla-monitor-service.yaml"
echo "  ✓ cms-sla-monitor-service"

oc apply -f "$OPENSHIFT_DIR/deployment-assignment-service.yaml"
echo "  ✓ cms-assignment-service"

oc apply -f "$OPENSHIFT_DIR/deployment-workflow-service.yaml"
echo "  ✓ cms-workflow-service"

oc apply -f "$OPENSHIFT_DIR/deployment-ingestion-service.yaml"
echo "  ✓ cms-ingestion-service"

oc apply -f "$OPENSHIFT_DIR/deployment-outbox-publisher.yaml"
echo "  ✓ cms-outbox-publisher"

echo ""
echo "[Phase 6] Backend (depends on all services)"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/deployment-backend.yaml"
echo "  ✓ cms-backend"

echo ""
echo "[Phase 7] API Gateway"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/deployment-api-gateway.yaml"
echo "  ✓ cms-api-gateway"

echo ""
echo "[Phase 8] Frontends (depend on backend/gateway)"
echo "---------------------------------------------"
oc apply -f "$OPENSHIFT_DIR/deployment-portal-frontend.yaml"
echo "  ✓ cms-portal-frontend (public)"

oc apply -f "$OPENSHIFT_DIR/deployment-frontend.yaml"
echo "  ✓ cms-frontend (staff)"

echo ""
echo "============================================="
echo "  Deployment Complete!"
echo "  Waiting for rollouts..."
echo "============================================="

echo ""
echo "[Phase 9] Verifying rollout status"
echo "---------------------------------------------"

SERVICES=(
  "cms-audit-service"
  "cms-storage-service"
  "cms-paddle-ocr"
  "cms-notification-service"
  "cms-search-service"
  "cms-rules-service"
  "cms-eligibility-service"
  "cms-sla-monitor-service"
  "cms-assignment-service"
  "cms-workflow-service"
  "cms-ingestion-service"
  "cms-outbox-publisher"
  "cms-backend"
  "cms-api-gateway"
  "cms-portal-frontend"
  "cms-frontend"
)

FAILED=0
for svc in "${SERVICES[@]}"; do
  if oc rollout status deployment/"$svc" -n rbi-cms --timeout=120s 2>/dev/null; then
    echo "  ✓ $svc is ready"
  else
    echo "  ✗ $svc FAILED to become ready"
    FAILED=$((FAILED + 1))
  fi
done

echo ""
if [ $FAILED -eq 0 ]; then
  echo "============================================="
  echo "  All 16 services deployed successfully!"
  echo "============================================="
else
  echo "============================================="
  echo "  WARNING: $FAILED service(s) failed rollout"
  echo "  Run: oc get pods -n rbi-cms"
  echo "============================================="
  exit 1
fi
