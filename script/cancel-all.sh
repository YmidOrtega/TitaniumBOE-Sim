# Cancel all pending orders

connect localhost 8081
sleep 1

# Get list of active orders first
echo "Fetching active orders..."
# Note: You'll need to add an "orders" command to list active orders

cancel CLI-12345
sleep 1

cancel CLI-12346
sleep 1

cancel CLI-12347
sleep 1

echo "All orders cancelled"