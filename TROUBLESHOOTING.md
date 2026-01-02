# Troubleshooting Guide

## Common Issues and Solutions

### 1. Application Won't Start

#### Issue: "Port 8080 already in use"
**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process or change server port
# In application.properties:
server.port=8081
```

#### Issue: "Cannot connect to Kafka broker"
**Solution:**
```bash
# Check if Kafka is running
docker compose ps kafka

# View Kafka logs
docker compose logs -f kafka

# Restart Kafka
docker compose restart kafka

# Wait for Kafka to be fully ready (30 seconds)
sleep 30
```

#### Issue: "Bean creation error for SqsClient"
**Solution:**
- Ensure LocalStack is running: `docker ps | grep localstack`
- Check receive.protocol and send.protocol match your intention
- If not using SQS, ensure protocol is NOT set to 'sqs'

### 2. Docker Compose Issues

#### Issue: "Cannot start service: port is already allocated"
**Solution:**
```bash
# Stop all services
docker compose down -v

# Check which process is using the port
lsof -i :9092  # or other conflicting port

# Either stop that process or change port in docker-compose.yml
```

#### Issue: "LocalStack not responding"
**Solution:**
```bash
# Check LocalStack logs
docker compose logs localstack

# Restart LocalStack
docker compose restart localstack

# Test LocalStack
aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1
```

### 3. Messaging Issues

#### Issue: "Messages not being consumed"
**Solution:**
```bash
# For Kafka - check if topic exists
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Create topic manually if needed
docker exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic new-orders \
  --partitions 1 \
  --replication-factor 1

# Check consumer group
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-api-group \
  --describe
```

#### Issue: "SQS queue not found"
**Solution:**
```bash
# Create queue manually
aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name new-orders \
  --region us-east-1

# List all queues
aws --endpoint-url=http://localhost:4566 sqs list-queues \
  --region us-east-1
```

#### Issue: "MQTT connection refused"
**Solution:**
```bash
# Check Mosquitto is running
docker compose ps mosquitto

# Test MQTT connection
docker exec mosquitto mosquitto_sub -h localhost -t test

# View Mosquitto logs
docker compose logs -f mosquitto
```

#### Issue: "RabbitMQ queue not created"
**Solution:**
```bash
# Access RabbitMQ management UI
# http://localhost:15672 (guest/guest)

# Or use CLI to create queue
docker exec rabbitmq rabbitmqadmin declare queue name=new-orders
```

### 4. Build Issues

#### Issue: "Maven build fails"
**Solution:**
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Skip tests if needed
./gradlew clean build -x test

# Check Java version (needs 17+)
java -version
```

#### Issue: "Dependency resolution error"
**Solution:**
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches

# Rebuild
./gradlew clean build --refresh-dependencies
```

### 5. Protocol-Specific Issues

#### Issue: "Kafka partition rebalancing"
**Solution:**
- Wait for rebalancing to complete (usually 10-30 seconds)
- Increase `KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS` in docker-compose.yml

#### Issue: "SQS messages not visible"
**Solution:**
- Check visibility timeout (default 30 seconds)
- Ensure message was sent to correct queue URL
- Use `receive-message` with `--wait-time-seconds 20` for long polling

#### Issue: "MQTT QoS issues"
**Solution:**
```properties
# In application.properties
mqtt.qos=1  # At least once delivery
# or
mqtt.qos=2  # Exactly once delivery
```

#### Issue: "ActiveMQ Artemis authentication failed"
**Solution:**
```properties
# Verify credentials in application.properties
spring.artemis.user=admin
spring.artemis.password=admin
```

### 6. Message Format Issues

#### Issue: "JSON parse error"
**Solution:**
- Ensure message is valid JSON
- Check correlation ID is in the correct location
- Verify field names match model classes

**Valid message format:**
```json
{
  "orderCorrelationId": "12345",
  "payload": {
    "id": 10,
    "orderItems": [
      {
        "id": 1,
        "name": "Product",
        "quantity": 1,
        "price": 100.0
      }
    ]
  }
}
```

#### Issue: "Correlation ID not found"
**Solution:**
- Ensure `orderCorrelationId` is set in message wrapper
- For protocols with headers (Kafka, SQS), set header as well
- Check listener is extracting correlation ID correctly

### 7. Performance Issues

#### Issue: "Slow message processing"
**Solution:**
```properties
# Increase consumer threads (Kafka)
spring.kafka.listener.concurrency=3

# Increase JMS connections
spring.jms.cache.session-cache-size=10
```

#### Issue: "Memory issues"
**Solution:**
```bash
# Increase JVM memory
export GRADLE_OPTS="-Xmx1024m"
./gradlew bootRun
```

### 8. Testing Issues

#### Issue: "Test script fails"
**Solution:**
```bash
# Ensure all services are up
docker compose ps

# Check if application is running
curl http://localhost:8080/actuator/health || echo "App not running"

# Run test with verbose output
bash -x test-order-api.sh
```

### 9. Logging and Debugging

#### Enable Debug Logging
```properties
# In application.properties
logging.level.com.example.orderapi=DEBUG
logging.level.org.springframework.kafka=DEBUG
logging.level.io.awspring.cloud=DEBUG
logging.level.org.springframework.amqp=DEBUG
```

#### View Application Logs
```bash
# If running with Maven
# Logs appear in console

# If running as jar
java -jar target/order-api-1.0.0.jar > app.log 2>&1

# View logs
tail -f app.log
```

#### View Broker Logs
```bash
# Kafka
docker compose logs -f kafka

# LocalStack (SQS)
docker compose logs -f localstack

# Mosquitto (MQTT)
docker compose logs -f mosquitto

# RabbitMQ
docker compose logs -f rabbitmq

# ActiveMQ Artemis
docker compose logs -f artemis
```

### 10. Environment Issues

#### Issue: "Docker not found"
**Solution:**
```bash
# Install Docker Desktop
# macOS: https://docs.docker.com/desktop/install/mac-install/
# Linux: https://docs.docker.com/engine/install/

# Verify installation
docker --version
docker compose --version
```

#### Issue: "Java version mismatch"
**Solution:**
```bash
# Check version
java -version

# Install Java 17 (macOS)
brew install openjdk@17

# Set JAVA_HOME
export JAVA_HOME=/usr/local/opt/openjdk@17
```

## Quick Diagnostic Commands

```bash
# Check all services
docker compose ps

# Check application health
curl http://localhost:8080/actuator/health

# Check Kafka topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check SQS queues
aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1

# Check RabbitMQ queues
docker exec rabbitmq rabbitmqctl list_queues

# Check ActiveMQ queues
# Visit http://localhost:8161

# Network connectivity
docker network inspect specmatic-async-sample_order-api-network
```

## Reset Everything

If all else fails, start fresh:

```bash
# Stop everything
docker compose down -v
pkill -f "gradlew bootRun"

# Remove all data
rm -rf localstack-data/
rm -rf mosquitto/data/*
rm -rf mosquitto/log/*
rm -rf build/

# Rebuild
./gradlew clean build

# Restart infrastructure
docker compose up -d

# Wait for services
sleep 30

# Run application
./start.sh
```

## Getting Help

1. Check application logs for specific error messages
2. Check broker logs via `docker compose logs [service]`
3. Verify configuration in application.properties
4. Ensure protocol beans are loaded (check startup logs)
5. Test broker connectivity independently

## Contact

For additional support, check:
- AsyncAPI Specification: `src/main/resources/asyncapi.yaml`
- Application Logs: Console output or log files
- Broker Management UIs (RabbitMQ, Artemis)

## LocalStack (SQS) Specific Issues

#### Issue: "Connection refused: localhost/127.0.0.1:4566"
**Solution:**

1. **Verify LocalStack is running:**
   ```bash
   docker ps | grep localstack
   ```

2. **Check LocalStack logs:**
   ```bash
   docker compose logs -f localstack
   ```

3. **Run verification script:**
   ```bash
   ./verify-localstack.sh
   ```

4. **Manually test LocalStack:**
   ```bash
   # Check health endpoint
   curl http://localhost:4566/_localstack/health
   
   # Test SQS
   aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1
   ```

5. **Restart LocalStack:**
   ```bash
   docker compose restart localstack
   sleep 15  # Wait for startup
   ```

6. **If still failing, recreate LocalStack:**
   ```bash
   docker compose down
   rm -rf localstack-data/
   docker compose up -d localstack
   sleep 20
   ```

#### Issue: "LocalStack takes too long to start"
**Solution:**
```bash
# Wait for LocalStack to be fully ready
docker compose up -d localstack
echo "Waiting for LocalStack to start..."
until curl -s http://localhost:4566/_localstack/health | grep -q "sqs"; do
    echo "Still waiting..."
    sleep 2
done
echo "LocalStack is ready!"
```

#### Issue: "Queue not found" errors
**Solution:**
The application auto-creates queues, but you can pre-create them:
```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name new-orders --region us-east-1

aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name wip-orders --region us-east-1

aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name to-be-cancelled-orders --region us-east-1

aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name cancelled-orders --region us-east-1

aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name accepted-orders --region us-east-1

aws --endpoint-url=http://localhost:4566 sqs create-queue \
  --queue-name out-for-delivery-orders --region us-east-1
```

#### Issue: "AWS CLI not found"
**Solution:**
```bash
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Verify
aws --version
```

#### LocalStack Port Conflicts
**Solution:**
```bash
# Check what's using port 4566
lsof -i :4566

# If something else is using it, stop it or change LocalStack port
# in docker-compose.yml:
# ports:
#   - "4567:4566"  # Map to different host port
# 
# Then update application-sqs-*.properties:
# spring.cloud.aws.endpoint=http://localhost:4567
```

