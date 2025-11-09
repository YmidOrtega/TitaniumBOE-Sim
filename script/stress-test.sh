# Stress test script - Submit multiple orders

echo "=== Stress Test - 10 Orders ==="

connect localhost 8081
sleep 2

order buy AAPL 10 149.00
sleep 1

order buy AAPL 20 149.25
sleep 1

order buy AAPL 30 149.50
sleep 1

order buy MSFT 15 379.00
sleep 1

order buy MSFT 25 379.50
sleep 1

order sell AAPL 10 150.50
sleep 1

order sell AAPL 20 150.75
sleep 1

order sell MSFT 15 381.00
sleep 1

order sell GOOGL 30 140.00
sleep 1

order sell GOOGL 40 140.50
sleep 1

echo "All orders submitted!"
positions