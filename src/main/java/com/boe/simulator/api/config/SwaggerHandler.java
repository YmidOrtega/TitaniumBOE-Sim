package com.boe.simulator.api.config;

import io.javalin.http.Context;

public class SwaggerHandler {

    public static void handle(Context ctx) {
        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>TitaniumBOE API - Swagger UI</title>
                <link rel="stylesheet" type="text/css" href="/swagger-ui/swagger-ui.css">
                <style>
                    html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                    *, *:before, *:after { box-sizing: inherit; }
                    body { margin:0; padding:0; background: #fafafa; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="/swagger-ui/swagger-ui-bundle.js"></script>
                <script src="/swagger-ui/swagger-ui-standalone-preset.js"></script>
                <script>
                window.onload = function() {
                    const ui = SwaggerUIBundle({
                        url: "/openapi",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout"
                    });
                    window.ui = ui;
                }
                </script>
            </body>
            </html>
            """;
        ctx.html(html);
    }
}
