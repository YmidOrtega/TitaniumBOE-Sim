package com.boe.simulator.api.service;

import com.boe.simulator.api.dto.PositionDTO;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.matching.TradeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PositionService {
    private static final Logger LOGGER = Logger.getLogger(PositionService.class.getName());

    private final TradeRepository tradeRepository;

    public PositionService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public List<PositionDTO> getPositions(String username) {
        // Get all trades for this user
        List<Trade> trades = tradeRepository.findByUsername(username);

        // Calculate positions by symbol
        Map<String, Position> positions = new HashMap<>();

        for (Trade trade : trades) {
            String symbol = trade.getSymbol();
            positions.putIfAbsent(symbol, new Position(symbol));

            Position position = positions.get(symbol);

            // Determine if this user was buyer or seller
            boolean isBuyer = trade.getBuyUsername().equals(username);

            if (isBuyer) position.addBuy(trade.getQuantity(), trade.getPrice());
            else position.addSell(trade.getQuantity(), trade.getPrice());
        }

        return positions.values().stream()
                .filter(p -> p.getNetQuantity() != 0)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<PositionDTO> getPosition(String username, String symbol) {
        List<Trade> trades = tradeRepository.findByUsername(username).stream()
                .filter(t -> t.getSymbol().equals(symbol))
                .toList();

        if (trades.isEmpty()) return Optional.empty();

        Position position = new Position(symbol);

        for (Trade trade : trades) {
            boolean isBuyer = trade.getBuyUsername().equals(username);

            if (isBuyer) position.addBuy(trade.getQuantity(), trade.getPrice());
            else position.addSell(trade.getQuantity(), trade.getPrice());

        }

        return position.getNetQuantity() != 0
                ? Optional.of(toDTO(position))
                : Optional.empty();
    }

    private PositionDTO toDTO(Position position) {
        // For demo purposes, current price = last trade price or avg price
        BigDecimal currentPrice = position.getAvgPrice();

        BigDecimal unrealizedPnL = calculateUnrealizedPnL(
                position.getNetQuantity(),
                position.getAvgPrice(),
                currentPrice
        );

        return new PositionDTO(
                position.getSymbol(),
                position.getNetQuantity(),
                position.getAvgPrice(),
                currentPrice,
                unrealizedPnL,
                position.getRealizedPnL()
        );
    }

    private BigDecimal calculateUnrealizedPnL(int quantity, BigDecimal avgPrice, BigDecimal currentPrice) {
        if (quantity == 0) return BigDecimal.ZERO;

        BigDecimal costBasis = avgPrice.multiply(BigDecimal.valueOf(Math.abs(quantity)));
        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(Math.abs(quantity)));

        return quantity > 0 ? currentValue.subtract(costBasis) : costBasis.subtract(currentValue);
    }

    // Helper class for position calculation
    private static class Position {
        private final String symbol;
        private int totalBuyQty = 0;
        private BigDecimal totalBuyCost = BigDecimal.ZERO;
        private int totalSellQty = 0;
        private BigDecimal totalSellProceeds = BigDecimal.ZERO;
        private BigDecimal realizedPnL = BigDecimal.ZERO;

        public Position(String symbol) {
            this.symbol = symbol;
        }

        public void addBuy(int qty, BigDecimal price) {
            totalBuyQty += qty;
            totalBuyCost = totalBuyCost.add(price.multiply(BigDecimal.valueOf(qty)));
        }

        public void addSell(int qty, BigDecimal price) {
            totalSellQty += qty;
            totalSellProceeds = totalSellProceeds.add(price.multiply(BigDecimal.valueOf(qty)));
        }

        public int getNetQuantity() {
            return totalBuyQty - totalSellQty;
        }

        public BigDecimal getAvgPrice() {
            int netQty = Math.abs(getNetQuantity());
            if (netQty == 0) return BigDecimal.ZERO;

            BigDecimal totalCost = getNetQuantity() > 0 ? totalBuyCost : totalSellProceeds;

            return totalCost.divide(BigDecimal.valueOf(netQty), 4, RoundingMode.HALF_UP);
        }

        public BigDecimal getRealizedPnL() {
            int closedQty = Math.min(totalBuyQty, totalSellQty);
            if (closedQty == 0) return BigDecimal.ZERO;

            BigDecimal avgBuyPrice = totalBuyCost.divide(
                    BigDecimal.valueOf(totalBuyQty), 4, RoundingMode.HALF_UP
            );
            BigDecimal avgSellPrice = totalSellProceeds.divide(
                    BigDecimal.valueOf(totalSellQty), 4, RoundingMode.HALF_UP
            );

            return avgSellPrice.subtract(avgBuyPrice).multiply(BigDecimal.valueOf(closedQty));
        }

        public String getSymbol() {
            return symbol;
        }
    }
}