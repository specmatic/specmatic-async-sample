#!/bin/bash
# ==============================================================================
# Specmatic Async Contract Tests - Combination Test Runner
# Runs combinations of docker-based ContractTest.kt across supported protocols:
#   kafka, sqs, mqtt, jms, amqp (25 total combinations)
# ==============================================================================

set -uo pipefail

# ANSI color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="$PROJECT_ROOT/build/reports/contract-tests"
LOG_DIR="$REPORT_DIR/logs"

DEFAULT_SPECMATIC_IMAGE="specmatic/enterprise:latest"
SPECMATIC_IMAGE="${SPECMATIC_DOCKER_IMAGE:-$DEFAULT_SPECMATIC_IMAGE}"

STOP_ON_FAILURE=false
FILTER_RECEIVE=""
FILTER_SEND=""

ALL_PROTOCOLS=("kafka" "sqs" "mqtt" "jms" "amqp")

show_help() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Runs Docker-based Specmatic contract tests (ContractTest.kt) across protocol combinations.

Options:
  -r, --receive <protocol>   Run only this receive protocol (kafka, sqs, mqtt, jms, amqp)
  -s, --send <protocol>      Run only this send protocol (kafka, sqs, mqtt, jms, amqp)
  -i, --image <image>        Specmatic Docker image to use (default: $DEFAULT_SPECMATIC_IMAGE)
      --stop-on-failure      Stop running further tests immediately when any test fails
  -h, --help                 Show this help message

Examples:
  # Run all 25 combinations:
  ./$(basename "$0")

  # Run a single combination (reproduce a specific failure):
  ./$(basename "$0") --receive sqs --send kafka

  # Use a specific Specmatic image:
  ./$(basename "$0") --image specmatic/enterprise:1.27.1
EOF
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--receive)
            FILTER_RECEIVE="$2"
            shift 2
            ;;
        -s|--send)
            FILTER_SEND="$2"
            shift 2
            ;;
        -i|--image)
            SPECMATIC_IMAGE="$2"
            shift 2
            ;;
        --stop-on-failure)
            STOP_ON_FAILURE=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

cd "$PROJECT_ROOT" || exit 1

# Pre-flight check: Docker
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}[ERROR] Docker daemon is not running or accessible.${NC}"
    echo "Please start Docker Desktop on your Mac and try again."
    exit 1
fi

# Pre-flight check: Gradle wrapper
if [[ ! -f "./gradlew" ]]; then
    echo -e "${RED}[ERROR] gradlew not found in $PROJECT_ROOT${NC}"
    exit 1
fi

# Build bootJar upfront once
echo -e "${BLUE}${BOLD}=== Building application bootJar ===${NC}"
if ! ./gradlew bootJar --quiet; then
    echo -e "${RED}[ERROR] Failed to build bootJar. Please fix compilation issues first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ bootJar build successful.${NC}\n"

# Prepare directories
mkdir -p "$LOG_DIR"
SUMMARY_MD="$REPORT_DIR/summary.md"

# Determine matrix to run
RECEIVE_LIST=()
SEND_LIST=()

if [[ -n "$FILTER_RECEIVE" ]]; then
    RECEIVE_LIST=("$FILTER_RECEIVE")
else
    RECEIVE_LIST=("${ALL_PROTOCOLS[@]}")
fi

if [[ -n "$FILTER_SEND" ]]; then
    SEND_LIST=("$FILTER_SEND")
else
    SEND_LIST=("${ALL_PROTOCOLS[@]}")
fi

TOTAL_TESTS=$((${#RECEIVE_LIST[@]} * ${#SEND_LIST[@]}))
CURRENT=0
PASSED_COUNT=0
FAILED_COUNT=0

# Arrays to store results for summary
declare -a RES_RECEIVE=()
declare -a RES_SEND=()
declare -a RES_STATUS=()
declare -a RES_DURATION=()
declare -a RES_LOGFILE=()

echo -e "${CYAN}${BOLD}========================================================================${NC}"
echo -e "${CYAN}${BOLD} Specmatic Async Contract Tests - Combination Matrix Runner            ${NC}"
echo -e "${CYAN}${BOLD} Total Combinations to run: $TOTAL_TESTS                               ${NC}"
echo -e "${CYAN}${BOLD} Specmatic Image: $SPECMATIC_IMAGE                                     ${NC}"
echo -e "${CYAN}${BOLD} Logs Directory:  $LOG_DIR                                             ${NC}"
echo -e "${CYAN}${BOLD}========================================================================${NC}\n"

START_TOTAL_TIME=$(date +%s)

for rec in "${RECEIVE_LIST[@]}"; do
    for snd in "${SEND_LIST[@]}"; do
        CURRENT=$((CURRENT + 1))
        LOG_FILE="$LOG_DIR/${rec}_to_${snd}.log"
        
        echo -e "${BOLD}[$CURRENT/$TOTAL_TESTS] Running: receive=${rec}, send=${snd}${NC}"
        
        START_TIME=$(date +%s)
        
        set +e
        ./gradlew test --tests "com.example.orderapi.ContractTest" \
            -Dreceive.protocol="$rec" \
            -Dsend.protocol="$snd" \
            -DspecmaticDockerImage="$SPECMATIC_IMAGE" \
            --rerun-tasks > "$LOG_FILE" 2>&1
        TEST_EXIT_CODE=$?
        set -e
        
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        
        RES_RECEIVE+=("$rec")
        RES_SEND+=("$snd")
        RES_DURATION+=("${DURATION}s")
        RES_LOGFILE+=("$LOG_FILE")
        
        if [[ $TEST_EXIT_CODE -eq 0 ]]; then
            RES_STATUS+=("PASSED")
            PASSED_COUNT=$((PASSED_COUNT + 1))
            echo -e "  └─ ${GREEN}✓ PASSED${NC} in ${DURATION}s"
        else
            RES_STATUS+=("FAILED")
            FAILED_COUNT=$((FAILED_COUNT + 1))
            echo -e "  └─ ${RED}✗ FAILED${NC} in ${DURATION}s (Exit code: $TEST_EXIT_CODE)"
            echo -e "     ${YELLOW}Log: file://$LOG_FILE${NC}"
            
            # Print failure message if present in log
            FAIL_MSG=$(grep -E "Result: FAILED|Failures: [1-9]|FAILURE: Build failed" "$LOG_FILE" | head -n 3 || true)
            if [[ -n "$FAIL_MSG" ]]; then
                echo -e "     ${RED}${FAIL_MSG}${NC}"
            fi
            
            if [[ "$STOP_ON_FAILURE" = true ]]; then
                echo -e "\n${RED}Stopping immediately due to --stop-on-failure flag.${NC}"
                break 2
            fi
        fi
        echo ""
    done
done

END_TOTAL_TIME=$(date +%s)
TOTAL_DURATION=$((END_TOTAL_TIME - START_TOTAL_TIME))

# ==============================================================================
# Generate Terminal Report & Markdown Report
# ==============================================================================

echo -e "\n${BOLD}========================================================================${NC}"
echo -e "${BOLD}                     TEST EXECUTION SUMMARY                             ${NC}"
echo -e "${BOLD}========================================================================${NC}"
printf "%-4s | %-12s | %-12s | %-10s | %-10s\n" "#" "Receive" "Send" "Status" "Duration"
echo "-----+--------------+--------------+------------+-----------"

for i in "${!RES_RECEIVE[@]}"; do
    IDX=$((i + 1))
    REC="${RES_RECEIVE[$i]}"
    SND="${RES_SEND[$i]}"
    ST="${RES_STATUS[$i]}"
    DUR="${RES_DURATION[$i]}"
    
    if [[ "$ST" == "PASSED" ]]; then
        ST_COLORED="${GREEN}${ST}${NC}"
    else
        ST_COLORED="${RED}${ST}${NC}"
    fi
    printf "%-4s | %-12s | %-12s | %-19b | %-10s\n" "$IDX" "$REC" "$SND" "$ST_COLORED" "$DUR"
done

echo "------------------------------------------------------------------------"
echo -e "Total: ${BOLD}$TOTAL_TESTS${NC} | Passed: ${GREEN}${BOLD}$PASSED_COUNT${NC} | Failed: ${RED}${BOLD}$FAILED_COUNT${NC} | Total Time: ${BOLD}${TOTAL_DURATION}s${NC}"
echo -e "========================================================================\n"

# Write markdown report
cat << EOF > "$SUMMARY_MD"
# Specmatic Async Contract Tests - Combination Report

- **Date:** $(date)
- **Specmatic Image:** \`$SPECMATIC_IMAGE\`
- **Total Combinations:** $TOTAL_TESTS
- **Passed:** $PASSED_COUNT
- **Failed:** $FAILED_COUNT
- **Total Duration:** ${TOTAL_DURATION}s

## Results Table

| # | Receive Protocol | Send Protocol | Status | Duration | Log File |
|---|---|---|---|---|---|
EOF

for i in "${!RES_RECEIVE[@]}"; do
    IDX=$((i + 1))
    REC="${RES_RECEIVE[$i]}"
    SND="${RES_SEND[$i]}"
    ST="${RES_STATUS[$i]}"
    DUR="${RES_DURATION[$i]}"
    LOG="${RES_LOGFILE[$i]}"
    ST_BADGE="✅ PASSED"
    if [[ "$ST" != "PASSED" ]]; then
        ST_BADGE="❌ FAILED"
    fi
    echo "| $IDX | \`$REC\` | \`$SND\` | $ST_BADGE | $DUR | [\`$(basename "$LOG")\`](file://$LOG) |" >> "$SUMMARY_MD"
done

# If there are failures, output repro commands
if [[ $FAILED_COUNT -gt 0 ]]; then
    echo -e "${RED}${BOLD}========================================================================${NC}"
    echo -e "${RED}${BOLD}                       FAILED COMBINATIONS                              ${NC}"
    echo -e "${RED}${BOLD}========================================================================${NC}"
    echo -e "The following combinations failed. Use the commands below to reproduce:"
    echo ""
    
    cat << EOF >> "$SUMMARY_MD"

## How to Reproduce Failures

The following combinations failed during this run:

EOF

    for i in "${!RES_RECEIVE[@]}"; do
        if [[ "${RES_STATUS[$i]}" != "PASSED" ]]; then
            REC="${RES_RECEIVE[$i]}"
            SND="${RES_SEND[$i]}"
            LOG="${RES_LOGFILE[$i]}"
            
            echo -e "${BOLD}Combination: receive=${REC}, send=${SND}${NC}"
            echo -e "  ${YELLOW}Log file:${NC} file://$LOG"
            echo -e "  ${CYAN}Reproduce via this script:${NC}"
            echo -e "    ./$(basename "$0") --receive $REC --send $SND"
            echo -e "  ${CYAN}Reproduce directly via Gradle:${NC}"
            echo -e "    ./gradlew test --tests \"com.example.orderapi.ContractTest\" -Dreceive.protocol=$REC -Dsend.protocol=$SND -DspecmaticDockerImage=$SPECMATIC_IMAGE --rerun-tasks"
            echo ""
            
            cat << EOF >> "$SUMMARY_MD"
### Failed: \`receive=$REC\`, \`send=$SND\`
- **Log file:** [\`$(basename "$LOG")\`](file://$LOG)
- **Reproduce via script:**
  \`\`\`bash
  ./$(basename "$0") --receive $REC --send $SND
  \`\`\`
- **Reproduce directly via Gradle:**
  \`\`\`bash
  ./gradlew test --tests "com.example.orderapi.ContractTest" -Dreceive.protocol=$REC -Dsend.protocol=$SND -DspecmaticDockerImage=$SPECMATIC_IMAGE --rerun-tasks
  \`\`\`

EOF
        fi
    done
    echo -e "Summary report saved to: ${BLUE}file://$SUMMARY_MD${NC}\n"
    exit 1
else
    echo -e "${GREEN}${BOLD}All tests passed successfully! 🎉${NC}"
    echo -e "Summary report saved to: ${BLUE}file://$SUMMARY_MD${NC}\n"
    exit 0
fi
