package com.boe.simulator.api.config;

import io.javalin.http.Handler;

public class ScalarHandler {
    
    public static Handler getHandler() {
        return ctx -> {
            String html = """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>TitaniumBOE Simulator API - Documentation</title>
                    <meta name="description" content="Documentación interactiva de la API REST del simulador TitaniumBOE">
                    <style>
                        body { margin: 0; padding: 0; }
                    </style>
                </head>
                <body>
                    <script
                        id="api-reference"
                        data-url="/openapi"></script>
                    <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                </body>
                </html>
                """;
            ctx.html(html);
        };
    }
}
