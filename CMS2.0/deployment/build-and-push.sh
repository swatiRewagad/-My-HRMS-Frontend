#!/bin/bash
set -e

SERVICE_NAME="${1:?Usage: $0 <service-name> [tag]}"
IMAGE_TAG="${2:-latest}"
REGISTRY="image-registry.openshift-image-registry.svc:5000/rbi-cms"

echo "Building $SERVICE_NAME with tag $IMAGE_TAG"

# Determine service directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Find the service directory
if [ -d "$PROJECT_ROOT/$SERVICE_NAME" ]; then
  SERVICE_DIR="$PROJECT_ROOT/$SERVICE_NAME"
elif [ -d "$PROJECT_ROOT/cms-$SERVICE_NAME" ]; then
  SERVICE_DIR="$PROJECT_ROOT/cms-$SERVICE_NAME"
else
  echo "ERROR: Cannot find directory for $SERVICE_NAME"
  exit 1
fi

echo "Source: $SERVICE_DIR"
echo "Image:  $REGISTRY/$SERVICE_NAME:$IMAGE_TAG"

# Build the image
podman build -t "$REGISTRY/$SERVICE_NAME:$IMAGE_TAG" "$SERVICE_DIR"

# Push to OpenShift internal registry
podman push "$REGISTRY/$SERVICE_NAME:$IMAGE_TAG"

echo ""
echo "✓ $SERVICE_NAME:$IMAGE_TAG pushed to registry"
echo ""
echo "To deploy:"
echo "  oc rollout restart deployment/$SERVICE_NAME -n rbi-cms"
