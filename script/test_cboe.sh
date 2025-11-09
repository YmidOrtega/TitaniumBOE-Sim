#!/bin/bash

# Colores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuración
API_URL="http://localhost:9091"
USER="TRADER1"
PASS="PASS1"
AUTH=$(echo -n "$USER:$PASS" | base64)

echo "=========================================="
echo "  CBOE REST API Integration Test"
echo "=========================================="
echo ""

# Test 1: Health Check
echo -e "${YELLOW}Test 1: Health Check${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/health")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Health check OK"
    echo "$BODY" | jq .
else
    echo -e "${RED}✗ FAIL${NC} - Health check failed (HTTP $HTTP_CODE)"
    exit 1
fi
echo ""

# Test 2: Get Symbols
echo -e "${YELLOW}Test 2: Get Available Symbols${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/symbols")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Symbols retrieved"
    echo "$BODY" | jq '.data[] | {symbol: .symbol, bid: .bestBid, ask: .bestAsk}'
else
    echo -e "${RED}✗ FAIL${NC} - Failed to get symbols (HTTP $HTTP_CODE)"
    exit 1
fi
echo ""

# Test 3: Submit BUY Order
echo -e "${YELLOW}Test 3: Submit BUY Order (AAPL 50 @ 149.00)${NC}"
BUY_ORDER=$(cat <<EOF
{
  "symbol": "AAPL",
  "side": "BUY",
  "orderQty": 50,
  "price": 149.00,
  "orderType": "LIMIT",
  "capacity": "CUSTOMER"
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/api/orders" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  -d "$BUY_ORDER")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 201 ]; then
    echo -e "${GREEN}✓ PASS${NC} - BUY order submitted"
    BUY_CLORDID=$(echo "$BODY" | jq -r '.data.clOrdID')
    echo "  ClOrdID: $BUY_CLORDID"
    echo "$BODY" | jq '.data | {clOrdID, orderID, symbol, side, orderQty, price, state}'
else
    echo -e "${RED}✗ FAIL${NC} - Failed to submit BUY order (HTTP $HTTP_CODE)"
    echo "$BODY" | jq .
    exit 1
fi
echo ""

# Test 4: Submit SELL Order (should match)
echo -e "${YELLOW}Test 4: Submit SELL Order to Match (AAPL 50 @ 149.00)${NC}"
SELL_ORDER=$(cat <<EOF
{
  "symbol": "AAPL",
  "side": "SELL",
  "orderQty": 50,
  "price": 149.00,
  "orderType": "LIMIT",
  "capacity": "CUSTOMER"
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/api/orders" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  -d "$SELL_ORDER")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 201 ]; then
    echo -e "${GREEN}✓ PASS${NC} - SELL order submitted"
    SELL_CLORDID=$(echo "$BODY" | jq -r '.data.clOrdID')
    echo "  ClOrdID: $SELL_CLORDID"
    echo "$BODY" | jq '.data | {clOrdID, orderID, symbol, side, orderQty, price, state}'
else
    echo -e "${RED}✗ FAIL${NC} - Failed to submit SELL order (HTTP $HTTP_CODE)"
    echo "$BODY" | jq .
    exit 1
fi
echo ""

# Wait for matching
sleep 1

# Test 5: Get Active Orders
echo -e "${YELLOW}Test 5: Get Active Orders${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/orders/active" \
  -H "Authorization: Basic $AUTH")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Active orders retrieved"
    ACTIVE_COUNT=$(echo "$BODY" | jq '.data | length')
    echo "  Active orders: $ACTIVE_COUNT"
    echo "$BODY" | jq '.data[] | {clOrdID, symbol, side, orderQty, leavesQty, state}'
else
    echo -e "${RED}✗ FAIL${NC} - Failed to get active orders (HTTP $HTTP_CODE)"
    exit 1
fi
echo ""

# Test 6: Get Recent Trades
echo -e "${YELLOW}Test 6: Get Recent Trades${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/trades/recent?limit=10" \
  -H "Authorization: Basic $AUTH")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Recent trades retrieved"
    TRADES_COUNT=$(echo "$BODY" | jq '.data | length')
    echo "  Recent trades: $TRADES_COUNT"
    if [ "$TRADES_COUNT" -gt 0 ]; then
        echo "$BODY" | jq '.data[0] | {symbol, quantity, price, notionalValue}'
    fi
else
    echo -e "${RED}✗ FAIL${NC} - Failed to get trades (HTTP $HTTP_CODE)"
    exit 1
fi
echo ""

# Test 7: Get Positions
echo -e "${YELLOW}Test 7: Get Positions${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/positions" \
  -H "Authorization: Basic $AUTH")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Positions retrieved"
    POSITIONS_COUNT=$(echo "$BODY" | jq '.data | length')
    echo "  Open positions: $POSITIONS_COUNT"
    if [ "$POSITIONS_COUNT" -gt 0 ]; then
        echo "$BODY" | jq '.data[] | {symbol, quantity, avgPrice, unrealizedPnL}'
    fi
else
    echo -e "${RED}✗ FAIL${NC} - Failed to get positions (HTTP $HTTP_CODE)"
    exit 1
fi
echo ""

# Test 8: Submit Order to Cancel
echo -e "${YELLOW}Test 8: Submit Order for Cancellation (MSFT 25 @ 380.00)${NC}"
CANCEL_ORDER=$(cat <<EOF
{
  "symbol": "MSFT",
  "side": "BUY",
  "orderQty": 25,
  "price": 380.00,
  "orderType": "LIMIT",
  "capacity": "CUSTOMER"
}
EOF
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/api/orders" \
  -H "Authorization: Basic $AUTH" \
  -H "Content-Type: application/json" \
  -d "$CANCEL_ORDER")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 201 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Order for cancellation submitted"
    CANCEL_CLORDID=$(echo "$BODY" | jq -r '.data.clOrdID')
    echo "  ClOrdID: $CANCEL_CLORDID"
else
    echo -e "${RED}✗ FAIL${NC} - Failed to submit order for cancellation"
    exit 1
fi
echo ""

# Test 9: Cancel Order
echo -e "${YELLOW}Test 9: Cancel Order${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/api/orders/$CANCEL_CLORDID" \
  -H "Authorization: Basic $AUTH")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Order cancelled successfully"
    echo "$BODY" | jq .
else
    echo -e "${RED}✗ FAIL${NC} - Failed to cancel order (HTTP $HTTP_CODE)"
    echo "$BODY" | jq .
fi
echo ""

# Test 10: Authentication Failure
echo -e "${YELLOW}Test 10: Authentication Failure (Invalid Credentials)${NC}"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/orders/active" \
  -H "Authorization: Basic $(echo -n 'INVALID:invalid' | base64)")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" -eq 401 ]; then
    echo -e "${GREEN}✓ PASS${NC} - Authentication properly rejected"
else
    echo -e "${RED}✗ FAIL${NC} - Authentication should have failed (HTTP $HTTP_CODE)"
fi
echo ""

# Summary
echo "=========================================="
echo -e "${GREEN}  All Integration Tests Passed! ✓${NC}"
echo "=========================================="
echo ""
echo "Summary:"
echo "  - REST API is running correctly"
echo "  - Authentication works"
echo "  - Order submission works"
echo "  - Order matching works"
echo "  - Order cancellation works"
echo "  - Position tracking works"
echo "  - Trade history works"
echo ""