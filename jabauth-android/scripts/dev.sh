#!/bin/bash
# JABAuth Development Helper Script

set -e

FRAMEWORK_VERSION=$(grep "FRAMEWORK_VERSION" gradle.properties | cut -d'=' -f2)

case "$1" in
  "build-framework")
    echo "🔨 Building all framework modules..."
    ./gradlew framework:core:build \
              framework:jabcode-sdk:build \
              framework:jabauth-client:build \
              framework:diagnostic-engine:build \
              framework:ui-components:build
    echo "✅ Framework build complete"
    ;;
    
  "test-framework")
    echo "🧪 Testing all framework modules..."
    ./gradlew framework:test
    echo "✅ Framework tests complete"
    ;;
    
  "publish-local")
    echo "📦 Publishing framework v${FRAMEWORK_VERSION} to Maven Local..."
    ./gradlew publishAllPublicationsToLocalRepository
    echo "✅ Published to ~/.m2/repository/com/jabauth/framework/"
    ;;
    
  "use-published")
    echo "🔄 Switching to published framework artifacts..."
    sed -i 's/USE_PUBLISHED_FRAMEWORK=false/USE_PUBLISHED_FRAMEWORK=true/' gradle.properties
    echo "✅ Now using published artifacts"
    ;;
    
  "use-project")
    echo "🔄 Switching to project dependencies..."
    sed -i 's/USE_PUBLISHED_FRAMEWORK=true/USE_PUBLISHED_FRAMEWORK=false/' gradle.properties
    echo "✅ Now using project dependencies"
    ;;
    
  "test-all")
    echo "🧪 Running all tests..."
    ./gradlew test
    echo "✅ All tests complete"
    ;;
    
  "clean")
    echo "🧹 Cleaning..."
    ./gradlew clean
    ;;
    
  *)
    echo "Usage: ./scripts/dev.sh <command>"
    echo "  build-framework | test-framework | publish-local"
    echo "  use-published | use-project | test-all | clean"
    ;;
esac
