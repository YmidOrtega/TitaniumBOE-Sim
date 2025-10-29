#!/bin/bash

# List of integration test classes
TESTS=(
    "SimpleLoginTest"
    "SingleHeartbeatTest"
    "BidirectionalHeartbeatTest"
    "MultiUserSessionTest"
    "ServerRobustnessTest"
    "ClientHeartbeatTest"
    "DuplicateSessionTest"
    "LoginRejectedTest"
    "LoginSuccessTest"
    "LogoutTest"
    "MultiClientTest"
    "ReLoginTest"
    "SequenceTest"
)

# Classpath
CP="target/boe-simulator-1.0-SNAPSHOT.jar:target/test-classes"
SERVER_LOG="server.log"

# Run tests in isolation
for TEST in "${TESTS[@]}"; do
    echo "=================================================="
    echo "RUNNING: ${TEST}"
    echo "=================================================="

    # Start server in the background
    java -cp target/boe-simulator-1.0-SNAPSHOT.jar com.boe.simulator.server.CboeServer > "${SERVER_LOG}" 2>&1 &
    SERVER_PID=$!
    echo "Server started with PID: ${SERVER_PID}"

    # Wait for server to be ready
    echo "Waiting for server to initialize..."
    timeout 10s grep -q "Ready to accept connections" <(tail -f "${SERVER_LOG}")
    if [ $? -ne 0 ]; then
        echo "Server failed to start!"
        kill "${SERVER_PID}"
        exit 1
    fi
    echo "Server is ready."

    # Run test
    java -cp "${CP}" "com.boe.simulator.integration.${TEST}"
    TEST_EXIT_CODE=$?

    # Stop server
    kill "${SERVER_PID}"
    wait "${SERVER_PID}" 2>/dev/null
    echo "Server stopped."
    rm "${SERVER_LOG}"

    if [ ${TEST_EXIT_CODE} -ne 0 ]; then
        echo "TEST FAILED: ${TEST}"
        exit 1
    fi
    echo ""
done

echo "=================================================="
echo "ALL INTEGRATION TESTS PASSED!"
echo "=================================================="