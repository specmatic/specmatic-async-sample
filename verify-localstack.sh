#!/bin/bash

echo "Verifying LocalStack setup..."
echo ""

# Check if LocalStack container is running
echo "1. Checking if LocalStack container is running..."
if docker ps | grep -q localstack; then
    echo "   ✅ LocalStack container is running"
else
    echo "   ❌ LocalStack container is not running"
    echo "   Starting LocalStack..."
    docker compose up -d localstack
    sleep 10
fi

echo ""

# Check if port 4566 is accessible
echo "2. Checking if port 4566 is accessible..."
if nc -z localhost 4566 2>/dev/null; then
    echo "   ✅ Port 4566 is accessible"
else
    echo "   ❌ Port 4566 is not accessible"
    echo "   Wait a few more seconds for LocalStack to start..."
    exit 1
fi

echo ""

# Check LocalStack health
echo "3. Checking LocalStack health..."
HEALTH=$(curl -s http://localhost:4566/_localstack/health 2>/dev/null || echo "FAILED")
if [[ $HEALTH == *"sqs"* ]]; then
    echo "   ✅ LocalStack SQS is healthy"
    echo "   Health response: $HEALTH"
else
    echo "   ⚠️  LocalStack might not be fully ready yet"
    echo "   Response: $HEALTH"
fi

echo ""

# Try to create a test queue
echo "4. Testing SQS functionality..."
aws --endpoint-url=http://localhost:4566 sqs create-queue \
    --queue-name test-queue \
    --region us-east-1 2>/dev/null

if [ $? -eq 0 ]; then
    echo "   ✅ Successfully created test queue"
    
    # List queues
    echo "   Listing queues..."
    aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1
else
    echo "   ❌ Failed to create test queue"
    echo "   Make sure AWS CLI is installed"
fi

echo ""
echo "════════════════════════════════════════════════════"
echo "LocalStack verification complete!"
echo ""
echo "If all checks passed, you can run:"
echo "  ./gradlew bootRun --args=\"--spring.profiles.active=sqs-sqs\""
echo "════════════════════════════════════════════════════"
