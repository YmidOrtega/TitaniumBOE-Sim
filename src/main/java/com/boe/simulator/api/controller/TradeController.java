package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.dto.TradeDTO;
import com.boe.simulator.api.service.TradeService;
import io.javalin.http.Context;

import java.util.List;

public class TradeController {
    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    public void getRecentTrades(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        List<TradeDTO> trades = tradeService.getRecentTrades(limit);
        ctx.json(ApiResponse.success(trades));
    }

    public void getTradesBySymbol(Context ctx) {
        String symbol = ctx.pathParam("symbol");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        List<TradeDTO> trades = tradeService.getTradesBySymbol(symbol, limit);
        ctx.json(ApiResponse.success(trades));
    }

    public void getUserTrades(Context ctx) {
        String username = ctx.attribute("username");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        List<TradeDTO> trades = tradeService.getUserTrades(username, limit);
        ctx.json(ApiResponse.success(trades));
    }
}