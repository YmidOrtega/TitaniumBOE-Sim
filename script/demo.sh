# CBOE CLI Demo Script
# This script demonstrates the interactive CLI

echo "=== CBOE BOE Protocol Demo ==="
echo ""

# Connect to server
echo "Step 1: Connecting to server..."
connect localhost 8081
sleep 2

# Check status
echo ""
echo "Step 2: Checking connection status..."
status
sleep 2

# View market data
echo ""
echo "Step 3: Viewing AAPL order book..."
book AAPL
sleep 2

# Submit buy order
echo ""
echo "Step 4: Submitting buy order..."
order buy AAPL 100 149.50
sleep 3

# Check positions
echo ""
echo "Step 5: Checking positions..."
positions
sleep 2

# View trades
echo ""
echo "Step 6: Viewing recent trades..."
trades
sleep 2

# Submit sell order
echo ""
echo "Step 7: Submitting sell order..."
order sell MSFT 50 380.00
sleep 3

echo ""
echo "=== Demo Complete ==="