package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.dto.PositionDTO;
import com.boe.simulator.api.service.PositionService;
import io.javalin.http.Context;

import java.util.List;

public class PositionController {
    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    public void getPositions(Context ctx) {
        String username = ctx.attribute("username");

        List<PositionDTO> positions = positionService.getPositions(username);
        ctx.json(ApiResponse.success(positions));
    }

    public void getPosition(Context ctx) {
        String username = ctx.attribute("username");
        String symbol = ctx.pathParam("symbol");

        positionService.getPosition(username, symbol)
                .ifPresentOrElse(
                        position -> ctx.json(ApiResponse.success(position)),
                        () -> {
                            ctx.json(ApiResponse.error("No position found for symbol: " + symbol));
                            ctx.status(404);
                        }
                );
    }
}