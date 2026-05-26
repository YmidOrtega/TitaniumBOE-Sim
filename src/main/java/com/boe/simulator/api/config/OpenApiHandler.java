package com.boe.simulator.api.config;

import io.javalin.http.Context;

public class OpenApiHandler {

    public static void handle(Context ctx, int port) {
        String spec = """
            {
              "openapi": "3.0.3",
              "info": {
                "title": "TitaniumBOE Simulator API",
                "description": "API REST para el simulador del protocolo CBOE Binary Order Entry (BOE). Incluye gestión de órdenes, posiciones, trades, autenticación y simulación de mercado.",
                "version": "1.0.0"
              },
              "servers": [
                {
                  "url": "http://localhost:%d",
                  "description": "Local development server"
                }
              ],
              "components": {
                "securitySchemes": {
                  "BasicAuth": {
                    "type": "http",
                    "scheme": "basic",
                    "description": "HTTP Basic Authentication (username:password)"
                  }
                },
                "schemas": {
                  "LoginRequest": {
                    "type": "object",
                    "required": ["username", "password"],
                    "properties": {
                      "username": {"type": "string", "example": "TRD1", "description": "1-4 alphanumeric chars"},
                      "password": {"type": "string", "example": "Pass1234!", "description": "6-10 chars"}
                    }
                  },
                  "RegisterRequest": {
                    "type": "object",
                    "required": ["username", "password"],
                    "properties": {
                      "username": {"type": "string", "example": "MYID", "description": "1-4 alphanumeric chars"},
                      "password": {"type": "string", "example": "MyPass1!", "description": "6-10 chars"}
                    }
                  },
                  "AuthResponse": {
                    "type": "object",
                    "properties": {
                      "username": {"type": "string"},
                      "message": {"type": "string"}
                    }
                  },
                  "OrderRequest": {
                    "type": "object",
                    "required": ["clOrdID", "symbol", "side", "orderQty", "ordType"],
                    "properties": {
                      "clOrdID": {"type": "string", "description": "Unique client order ID"},
                      "symbol": {"type": "string", "example": "AAPL"},
                      "side": {"type": "string", "enum": ["BUY", "SELL"]},
                      "orderQty": {"type": "integer", "example": 100},
                      "price": {"type": "number", "example": 150.50},
                      "ordType": {"type": "string", "enum": ["MARKET", "LIMIT"]}
                    }
                  },
                  "OrderResponse": {
                    "type": "object",
                    "properties": {
                      "clOrdID": {"type": "string"},
                      "symbol": {"type": "string"},
                      "side": {"type": "string"},
                      "orderQty": {"type": "integer"},
                      "price": {"type": "number"},
                      "ordStatus": {"type": "string"},
                      "timestamp": {"type": "integer", "format": "int64"}
                    }
                  },
                  "Symbol": {
                    "type": "object",
                    "properties": {
                      "symbol": {"type": "string"},
                      "name": {"type": "string"},
                      "bestBid": {"type": "number"},
                      "bestAsk": {"type": "number"},
                      "lastPrice": {"type": "number"},
                      "activeOrders": {"type": "integer"},
                      "status": {"type": "string"}
                    }
                  },
                  "Position": {
                    "type": "object",
                    "properties": {
                      "symbol": {"type": "string"},
                      "quantity": {"type": "integer"},
                      "avgPrice": {"type": "number"},
                      "unrealizedPnl": {"type": "number"}
                    }
                  },
                  "Trade": {
                    "type": "object",
                    "properties": {
                      "tradeId": {"type": "string"},
                      "symbol": {"type": "string"},
                      "side": {"type": "string"},
                      "quantity": {"type": "integer"},
                      "price": {"type": "number"},
                      "timestamp": {"type": "integer", "format": "int64"},
                      "buyUsername": {"type": "string"},
                      "sellUsername": {"type": "string"}
                    }
                  },
                  "BotInfo": {
                    "type": "object",
                    "properties": {
                      "botId": {"type": "string"},
                      "strategy": {"type": "string"},
                      "symbol": {"type": "string"},
                      "running": {"type": "boolean"},
                      "ordersPlaced": {"type": "integer"}
                    }
                  },
                  "SimulatorStatus": {
                    "type": "object",
                    "properties": {
                      "running": {"type": "boolean"},
                      "activeBots": {"type": "integer"},
                      "totalBots": {"type": "integer"}
                    }
                  },
                  "ApiResponse": {
                    "type": "object",
                    "properties": {
                      "success": {"type": "boolean"},
                      "data": {"type": "object"},
                      "error": {"type": "string"},
                      "timestamp": {"type": "integer", "format": "int64"}
                    }
                  },
                  "HealthResponse": {
                    "type": "object",
                    "properties": {
                      "status": {"type": "string", "example": "healthy"},
                      "timestamp": {"type": "integer", "format": "int64"},
                      "activeOrders": {"type": "integer"},
                      "totalMatches": {"type": "integer", "format": "int64"}
                    }
                  }
                }
              },
              "paths": {
                "/api/health": {
                  "get": {
                    "tags": ["System"],
                    "summary": "Health check",
                    "responses": {
                      "200": {
                        "description": "Server status",
                        "content": {"application/json": {"schema": {"$ref": "#/components/schemas/HealthResponse"}}}
                      }
                    }
                  }
                },
                "/api/auth/login": {
                  "post": {
                    "tags": ["Authentication"],
                    "summary": "Login",
                    "description": "Authenticate with username and password",
                    "requestBody": {
                      "required": true,
                      "content": {"application/json": {"schema": {"$ref": "#/components/schemas/LoginRequest"}}}
                    },
                    "responses": {
                      "200": {
                        "description": "Login successful",
                        "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiResponse"}}}
                      },
                      "400": {"description": "Missing fields"},
                      "401": {"description": "Invalid credentials"}
                    }
                  }
                },
                "/api/auth/register": {
                  "post": {
                    "tags": ["Authentication"],
                    "summary": "Register",
                    "description": "Create a new user account (username: 1-4 alphanumeric, password: 6-10 chars)",
                    "requestBody": {
                      "required": true,
                      "content": {"application/json": {"schema": {"$ref": "#/components/schemas/RegisterRequest"}}}
                    },
                    "responses": {
                      "201": {"description": "Account created"},
                      "400": {"description": "Validation error"},
                      "409": {"description": "Username already taken"}
                    }
                  }
                },
                "/api/auth/me": {
                  "get": {
                    "tags": ["Authentication"],
                    "summary": "Current user",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {"description": "Authenticated user info"},
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/symbols": {
                  "get": {
                    "tags": ["Market Data"],
                    "summary": "List all symbols",
                    "responses": {
                      "200": {
                        "description": "Symbol list",
                        "content": {"application/json": {"schema": {"type": "object", "properties": {"success": {"type": "boolean"}, "data": {"type": "array", "items": {"$ref": "#/components/schemas/Symbol"}}}}}}
                      }
                    }
                  }
                },
                "/api/symbols/{symbol}": {
                  "get": {
                    "tags": ["Market Data"],
                    "summary": "Get symbol details",
                    "parameters": [{"name": "symbol", "in": "path", "required": true, "schema": {"type": "string"}, "example": "AAPL"}],
                    "responses": {
                      "200": {"description": "Symbol details"},
                      "404": {"description": "Symbol not found"}
                    }
                  }
                },
                "/api/orders": {
                  "post": {
                    "tags": ["Orders"],
                    "summary": "Submit order",
                    "security": [{"BasicAuth": []}],
                    "requestBody": {
                      "required": true,
                      "content": {"application/json": {"schema": {"$ref": "#/components/schemas/OrderRequest"}}}
                    },
                    "responses": {
                      "201": {"description": "Order accepted"},
                      "400": {"description": "Invalid request"},
                      "401": {"description": "Not authenticated"},
                      "422": {"description": "Order rejected"}
                    }
                  }
                },
                "/api/orders/active": {
                  "get": {
                    "tags": ["Orders"],
                    "summary": "Get active orders",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {"description": "Active orders for authenticated user"},
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/orders/{clOrdID}": {
                  "get": {
                    "tags": ["Orders"],
                    "summary": "Get order by ID",
                    "security": [{"BasicAuth": []}],
                    "parameters": [{"name": "clOrdID", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Order found"},
                      "401": {"description": "Not authenticated"},
                      "404": {"description": "Order not found"}
                    }
                  },
                  "delete": {
                    "tags": ["Orders"],
                    "summary": "Cancel order",
                    "security": [{"BasicAuth": []}],
                    "parameters": [{"name": "clOrdID", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Order cancelled"},
                      "401": {"description": "Not authenticated"},
                      "404": {"description": "Order not found"}
                    }
                  }
                },
                "/api/positions": {
                  "get": {
                    "tags": ["Positions"],
                    "summary": "Get all positions",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {
                        "description": "User positions",
                        "content": {"application/json": {"schema": {"type": "object", "properties": {"success": {"type": "boolean"}, "data": {"type": "array", "items": {"$ref": "#/components/schemas/Position"}}}}}}
                      },
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/positions/{symbol}": {
                  "get": {
                    "tags": ["Positions"],
                    "summary": "Get position by symbol",
                    "security": [{"BasicAuth": []}],
                    "parameters": [{"name": "symbol", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Position for symbol"},
                      "401": {"description": "Not authenticated"},
                      "404": {"description": "No position for symbol"}
                    }
                  }
                },
                "/api/trades/recent": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "Recent trades",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {"description": "Last N market trades"},
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/trades/my": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "My trades",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {"description": "Trades for authenticated user"},
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/trades/symbol/{symbol}": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "Trades by symbol",
                    "security": [{"BasicAuth": []}],
                    "parameters": [{"name": "symbol", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Trades for symbol"},
                      "401": {"description": "Not authenticated"}
                    }
                  }
                },
                "/api/simulator/status": {
                  "get": {
                    "tags": ["Simulator"],
                    "summary": "Simulator status",
                    "responses": {
                      "200": {
                        "description": "Current simulator and bot status",
                        "content": {"application/json": {"schema": {"$ref": "#/components/schemas/SimulatorStatus"}}}
                      }
                    }
                  }
                },
                "/api/simulator/start": {
                  "post": {
                    "tags": ["Simulator"],
                    "summary": "Start simulator",
                    "description": "Start all trading bots",
                    "responses": {"200": {"description": "Simulator started"}}
                  }
                },
                "/api/simulator/stop": {
                  "post": {
                    "tags": ["Simulator"],
                    "summary": "Stop simulator",
                    "description": "Stop all trading bots",
                    "responses": {"200": {"description": "Simulator stopped"}}
                  }
                },
                "/api/simulator/bots": {
                  "get": {
                    "tags": ["Simulator"],
                    "summary": "List bots",
                    "responses": {
                      "200": {
                        "description": "All registered trading bots",
                        "content": {"application/json": {"schema": {"type": "array", "items": {"$ref": "#/components/schemas/BotInfo"}}}}
                      }
                    }
                  }
                },
                "/api/simulator/bots/{botId}": {
                  "get": {
                    "tags": ["Simulator"],
                    "summary": "Get bot",
                    "parameters": [{"name": "botId", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Bot details"},
                      "404": {"description": "Bot not found"}
                    }
                  }
                },
                "/api/simulator/bots/{botId}/start": {
                  "post": {
                    "tags": ["Simulator"],
                    "summary": "Start bot",
                    "parameters": [{"name": "botId", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Bot started"},
                      "404": {"description": "Bot not found"}
                    }
                  }
                },
                "/api/simulator/bots/{botId}/stop": {
                  "post": {
                    "tags": ["Simulator"],
                    "summary": "Stop bot",
                    "parameters": [{"name": "botId", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {
                      "200": {"description": "Bot stopped"},
                      "404": {"description": "Bot not found"}
                    }
                  }
                }
              },
              "tags": [
                {"name": "System",         "description": "Server health and metadata"},
                {"name": "Authentication", "description": "Login, register, session"},
                {"name": "Market Data",    "description": "Symbols and order book"},
                {"name": "Orders",         "description": "Order lifecycle"},
                {"name": "Positions",      "description": "User positions and P&L"},
                {"name": "Trades",         "description": "Executed trades"},
                {"name": "Simulator",      "description": "Trading bot management"}
              ]
            }
            """.formatted(port);

        ctx.contentType("application/json");
        ctx.result(spec);
    }
}
