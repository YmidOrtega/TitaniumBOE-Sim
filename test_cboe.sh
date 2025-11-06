#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Variables
HOST="http://localhost:9090"
USER="TRADER1"
PASS="PASS1"
AUTH=$(echo -n "$USER:$PASS" | base64)

# Function to check curl success
check_curl() {
  if [ "$1" -ne 200 ]; then
    echo "Error: Expected HTTP 200, but got $1"
    echo "Response body:"
    # The body is passed as the second argument
    echo "$2"
    exit 1
  fi
}

check_post_request() {
  if [ "$1" -ne 200 ] && [ "$1" -ne 201 ]; then
    echo "Error: Expected HTTP 200 or 201, but got $1"
    echo "Response body:"
    # The body is passed as the second argument
    echo "$2"
    exit 1
  fi
}

echo "========== Testing CBOE REST API =========="

# 1. Health Check
echo -e "\n1. Health Check:"
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$HOST/api/health")
HEALTH_BODY=$(echo "$HEALTH_RESPONSE" | sed '$d')
HEALTH_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)
check_curl "$HEALTH_CODE" "$HEALTH_BODY"
echo "$HEALTH_BODY" | jq .
echo "Health check OK"

# 2. Get Symbols
echo -e "\n2. Get Available Symbols:"
curl -s "$HOST/api/symbols" | jq .
echo "Symbols fetched"

# 3. Submit Buy Order
echo -e "\n3. Submit Buy Order (AAPL 100 @ 150.00):"
ORDER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$HOST/api/orders" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "side": "BUY",
    "orderQty": 100,
    "price": 150.00,
    "orderType": "LIMIT",
    "capacity": "CUSTOMER"
  }')

ORDER_BODY=$(echo "$ORDER_RESPONSE" | sed '$d')
HTTP_CODE=$(echo "$ORDER_RESPONSE" | tail -n1)
check_post_request "$HTTP_CODE" "$ORDER_BODY"

echo "$ORDER_BODY" | jq .
CL_ORD_ID=$(echo "$ORDER_BODY" | jq -r '.data.clOrdID')

if [ -z "$CL_ORD_ID" ] || [ "$CL_ORD_ID" == "null" ]; then
  echo "Error: clOrdID not found in order response."
  exit 1
fi
echo "Order submitted successfully. clOrdID: $CL_ORD_ID"

# 4. Get Active Orders
echo -e "\n4. Get Active Orders:"
curl -s -H "Authorization: Basic $AUTH" "$HOST/api/orders/active" | jq .

# 5. Cancel Order
echo -e "\n5. Cancel Order: $CL_ORD_ID"
CANCEL_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$HOST/api/orders/$CL_ORD_ID" \
  -H "Authorization: Basic $AUTH")

CANCEL_BODY=$(echo "$CANCEL_RESPONSE" | sed '$d')
HTTP_CODE=$(echo "$CANCEL_RESPONSE" | tail -n1)
check_curl "$HTTP_CODE" "$CANCEL_BODY"
echo "$CANCEL_BODY" | jq .
echo "Order cancellation request sent."

# Allow some time for the server to process the cancellation
sleep 1

# 6. Get Active Orders (should be empty or not contain the cancelled order)
echo -e "\n6. Get Active Orders (after cancellation):"
curl -s -H "Authorization: Basic $AUTH" "$HOST/api/orders/active" | jq .

# 7. Get Positions
echo -e "\n7. Get Positions:"
curl -s -H "Authorization: Basic $AUTH" "$HOST/api/positions" | jq .

# 8. Get Recent Trades
echo -e "\n8. Get Recent Trades:"
curl -s -H "Authorization: Basic $AUTH" "$HOST/api/trades/recent?limit=10" | jq .

echo -e "\n========== Test Complete =========="
"}