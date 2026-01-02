#!/bin/bash

# Order API Startup Script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_header "Order API Multi-Protocol Setup"

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi
print_success "Docker found"

# Check docker compose (v2)
if ! docker compose version &> /dev/null; then
    print_error "Docker Compose is not available. Please install Docker Compose (v2)."
    exit 1
fi
print_success "Docker Compose found"

if ! command -v java &> /dev/null; then
    print_error "Java is not installed. Please install Java 17 or higher."
    exit 1
fi
print_success "Java found"

# Start infrastructure
print_header "Starting Infrastructure Services"

echo "Starting Docker Compose services..."
docker compose up -d

echo ""
echo "Waiting for services to be ready..."
sleep 10

print_success "Kafka ready on port 9092"
print_success "LocalStack (SQS) ready on port 4566"
print_success "Mosquitto (MQTT) ready on port 1883"
print_success "RabbitMQ (AMQP) ready on port 5672"
print_success "ActiveMQ Artemis (JMS) ready on port 61616"

# Build application
print_header "Building Application"

echo "Running Gradle build..."
./gradlew clean build -x test

print_success "Build completed successfully"

# Protocol selection
print_header "Protocol Configuration"

echo "Select protocol combination:"
echo "1) Kafka to Kafka (recommended for testing)"
echo "2) Kafka to SQS"
echo "3) SQS to SQS"
echo "4) MQTT to MQTT"
echo "5) AMQP to AMQP"
echo "6) JMS to JMS"
echo "7) Custom (edit application.properties manually)"
echo ""
read -p "Enter choice [1-7]: " protocol_choice

case $protocol_choice in
    1)
        PROFILE="kafka-kafka"
        print_success "Selected: Kafka → Kafka"
        ;;
    2)
        PROFILE="kafka-sqs"
        print_success "Selected: Kafka → SQS"
        ;;
    3)
        PROFILE="sqs-sqs"
        print_success "Selected: SQS → SQS"
        ;;
    4)
        PROFILE="mqtt-mqtt"
        print_success "Selected: MQTT → MQTT"
        ;;
    5)
        PROFILE="amqp-amqp"
        print_success "Selected: AMQP → AMQP"
        ;;
    6)
        PROFILE="jms-jms"
        print_success "Selected: JMS → JMS"
        ;;
    7)
        PROFILE=""
        print_warning "Using default application.properties. Please edit it manually."
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac

# Start application
print_header "Starting Application"

echo "Starting Order API..."
echo ""

if [ -z "$PROFILE" ]; then
    ./gradlew bootRun
else
    ./gradlew bootRun --args="--spring.profiles.active=$PROFILE"
fi
