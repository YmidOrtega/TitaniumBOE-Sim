package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.service.SymbolService;
import com.boe.simulator.server.matching.MatchingEngine;
import io.javalin.http.Context;
import io.javalin.openapi.*;

import java.math.BigDecimal;
import java.util.List;

public class SymbolController {
    private final MatchingEngine matchingEngine;
    private final SymbolService symbolService;

    public SymbolController(MatchingEngine matchingEngine, SymbolService symbolService) {
        this.matchingEngine = matchingEngine;
        this.symbolService = symbolService;
    }

    @OpenApi(
        summary = "Get all available symbols",
        description = "Obtiene la lista de todos los símbolos disponibles para trading con su información de mercado",
        operationId = "getSymbols",
        path = "/api/symbols",
        methods = HttpMethod.GET,
        tags = {"Market Data"},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = SymbolDTO[].class)}, description = "Lista de símbolos disponibles")
        }
    )
    public void getSymbols(Context ctx) {
        List<SymbolDTO> symbolDTOs = symbolService.getAllSymbols().stream()
                .map(symbolInfo -> {
                    // Check if the symbol has an active order book
                    var bookOpt = matchingEngine.getOrderBook(symbolInfo.symbol());

                    if (bookOpt.isPresent()) {
                        var book = bookOpt.get();
                        return new SymbolDTO(
                                symbolInfo.symbol(),
                                symbolInfo.name(),
                                symbolInfo.exchange(),
                                book.getBestBid(),
                                book.getBestAsk(),
                                book.getLastTradePrice(),
                                book.size(),
                                "ACTIVE"
                        );
                    } else {
                        // Symbol available but no active trading
                        return new SymbolDTO(
                                symbolInfo.symbol(),
                                symbolInfo.name(),
                                symbolInfo.exchange(),
                                null,
                                null,
                                symbolInfo.referencePrice(),
                                0,
                                "AVAILABLE"
                        );
                    }
                })
                .toList();

        ctx.json(ApiResponse.success(symbolDTOs));
    }

    public void getSymbol(Context ctx) {
        String symbol = ctx.pathParam("symbol");

        var symbolInfo = symbolService.getSymbol(symbol);
        if (symbolInfo == null) {
            ctx.json(ApiResponse.error("Symbol not found: " + symbol));
            ctx.status(404);
            return;
        }

        var bookOpt = matchingEngine.getOrderBook(symbol);

        SymbolDTO dto;
        if (bookOpt.isPresent()) {
            var book = bookOpt.get();
            dto = new SymbolDTO(
                    symbolInfo.symbol(),
                    symbolInfo.name(),
                    symbolInfo.exchange(),
                    book.getBestBid(),
                    book.getBestAsk(),
                    book.getLastTradePrice(),
                    book.size(),
                    "ACTIVE"
            );
        } else {
            dto = new SymbolDTO(
                    symbolInfo.symbol(),
                    symbolInfo.name(),
                    symbolInfo.exchange(),
                    null,
                    null,
                    symbolInfo.referencePrice(),
                    0,
                    "AVAILABLE"
            );
        }

        ctx.json(ApiResponse.success(dto));
    }

    public void getOrderBook(Context ctx) {
        String symbol = ctx.pathParam("symbol");
        int depth = ctx.queryParamAsClass("depth", Integer.class).getOrDefault(10);

        var bookOpt = matchingEngine.getOrderBook(symbol);
        if (bookOpt.isEmpty()) {
            ctx.json(ApiResponse.error("No active order book for symbol: " + symbol));
            ctx.status(404);
            return;
        }

        var snapshot = bookOpt.get().getSnapshot(depth);
        ctx.json(ApiResponse.success(snapshot));
    }

    private record SymbolDTO(
            String symbol,
            String name,
            String exchange,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            BigDecimal lastPrice,
            int activeOrders,
            String status
    ) {}
}