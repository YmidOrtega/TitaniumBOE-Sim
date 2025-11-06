package com.boe.simulator.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record PositionDTO(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("avgPrice") BigDecimal avgPrice,
        @JsonProperty("currentPrice") BigDecimal currentPrice,
        @JsonProperty("unrealizedPnL") BigDecimal unrealizedPnL,
        @JsonProperty("realizedPnL") BigDecimal realizedPnL
) {}