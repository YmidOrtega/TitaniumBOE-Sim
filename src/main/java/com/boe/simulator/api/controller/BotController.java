package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.bot.Bot;
import com.boe.simulator.bot.BotManager;
import com.boe.simulator.bot.MarketSimulator;
import io.javalin.http.Context;

import java.util.List;
import java.util.stream.Collectors;

public class BotController {
    private final MarketSimulator marketSimulator;

    public BotController(MarketSimulator marketSimulator) {
        this.marketSimulator = marketSimulator;
    }

    public void getSimulatorStatus(Context ctx) {
        var stats = marketSimulator.getStatistics();
        ctx.json(ApiResponse.success(stats));
    }

    public void getAllBots(Context ctx) {
        BotManager botManager = marketSimulator.getBotManager();

        List<BotDTO> botDTOs = botManager.getAllBots().stream()
                .map(bot -> new BotDTO(
                        bot.getBotId(),
                        bot.getConfig().strategyType(),
                        bot.isRunning(),
                        bot.getStatistics().getTotalOrders(),
                        bot.getStatistics().getSuccessfulOrders(),
                        bot.getStatistics().getTotalVolume(),
                        bot.getStatistics().getSuccessRate()
                ))
                .collect(Collectors.toList());

        ctx.json(ApiResponse.success(botDTOs));
    }

    public void getBot(Context ctx) {
        String botId = ctx.pathParam("botId");
        BotManager botManager = marketSimulator.getBotManager();

        Bot bot = botManager.getBot(botId);
        if (bot == null) {
            ctx.json(ApiResponse.error("Bot not found: " + botId));
            ctx.status(404);
            return;
        }

        BotDTO dto = new BotDTO(
                bot.getBotId(),
                bot.getConfig().strategyType(),
                bot.isRunning(),
                bot.getStatistics().getTotalOrders(),
                bot.getStatistics().getSuccessfulOrders(),
                bot.getStatistics().getTotalVolume(),
                bot.getStatistics().getSuccessRate()
        );

        ctx.json(ApiResponse.success(dto));
    }

    public void startBot(Context ctx) {
        String botId = ctx.pathParam("botId");
        BotManager botManager = marketSimulator.getBotManager();

        boolean success = botManager.startBot(botId);

        if (success) ctx.json(ApiResponse.success("Bot started: " + botId));
        else {
            ctx.json(ApiResponse.error("Failed to start bot: " + botId));
            ctx.status(400);
        }
    }

    public void stopBot(Context ctx) {
        String botId = ctx.pathParam("botId");
        BotManager botManager = marketSimulator.getBotManager();

        boolean success = botManager.stopBot(botId);

        if (success) ctx.json(ApiResponse.success("Bot stopped: " + botId));
        else {
            ctx.json(ApiResponse.error("Failed to stop bot: " + botId));
            ctx.status(400);
        }
    }

    public void startSimulator(Context ctx) {
        if (marketSimulator.isRunning()) {
            ctx.json(ApiResponse.error("Simulator is already running"));
            ctx.status(400);
            return;
        }

        marketSimulator.start();
        ctx.json(ApiResponse.success("Market simulator started"));
    }

    public void stopSimulator(Context ctx) {
        if (!marketSimulator.isRunning()) {
            ctx.json(ApiResponse.error("Simulator is not running"));
            ctx.status(400);
            return;
        }

        marketSimulator.stop();
        ctx.json(ApiResponse.success("Market simulator stopped"));
    }

    private record BotDTO(
            String botId,
            String strategyType,
            boolean running,
            long totalOrders,
            long successfulOrders,
            long totalVolume,
            double successRate
    ) {}
}