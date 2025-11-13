package com.boe.simulator.api.config;

import io.javalin.http.Context;

public class OpenApiHandler {

    public static void handle(Context ctx, int port) {
        String spec = String.format("""
            {
              "openapi": "3.0.3",
              "info": {
                "title": "TitaniumBOE Simulator API",
                "description": "API REST para el simulador de Bolsa de Opciones y Futuros (BOE). Proporciona endpoints para gestión de órdenes, posiciones, trades y simulación de mercado.",
                "version": "1.0.0"
              },
              "servers": [
                {
                  "url": "http://localhost:%d",
                  "description": "Development Server"
                }
              ],
              "components": {
                "securitySchemes": {
                  "BasicAuth": {
                    "type": "http",
                    "scheme": "basic",
                    "description": "HTTP Basic Authentication"
                  }
                },
                "schemas": {
                  "OrderRequest": {
                    "type": "object",
                    "properties": {
                      "clOrdID": {"type": "string", "description": "ID único de la orden del cliente"},
                      "symbol": {"type": "string", "description": "Símbolo del instrumento"},
                      "side": {"type": "string", "enum": ["BUY", "SELL"], "description": "Lado de la orden"},
                      "orderQty": {"type": "integer", "description": "Cantidad de la orden"},
                      "price": {"type": "number", "description": "Precio de la orden"},
                      "ordType": {"type": "string", "enum": ["MARKET", "LIMIT"], "description": "Tipo de orden"}
                    },
                    "required": ["clOrdID", "symbol", "side", "orderQty", "ordType"]
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
                  "ApiResponse": {
                    "type": "object",
                    "properties": {
                      "success": {"type": "boolean"},
                      "data": {"type": "object"},
                      "error": {"type": "string"},
                      "timestamp": {"type": "integer", "format": "int64"}
                    }
                  },
                  "Symbol": {
                    "type": "object",
                    "properties": {
                      "symbol": {"type": "string"},
                      "name": {"type": "string"},
                      "exchange": {"type": "string"},
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
                      "averagePrice": {"type": "number"},
                      "currentPrice": {"type": "number"},
                      "unrealizedPnl": {"type": "number"},
                      "realizedPnl": {"type": "number"}
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
                      "buyOrderId": {"type": "string"},
                      "sellOrderId": {"type": "string"}
                    }
                  }
                }
              },
              "paths": {
                "/api/health": {
                  "get": {
                    "tags": ["System"],
                    "summary": "Health Check",
                    "description": "Verifica el estado del servidor",
                    "responses": {
                      "200": {
                        "description": "Servidor funcionando correctamente",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "status": {"type": "string"},
                                "timestamp": {"type": "integer"},
                                "activeOrders": {"type": "integer"},
                                "totalMatches": {"type": "integer"}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                },
                "/api/symbols": {
                  "get": {
                    "tags": ["Market Data"],
                    "summary": "Get all symbols",
                    "description": "Obtiene la lista de todos los símbolos disponibles para trading",
                    "responses": {
                      "200": {
                        "description": "Lista de símbolos",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "success": {"type": "boolean"},
                                "data": {
                                  "type": "array",
                                  "items": {"$ref": "#/components/schemas/Symbol"}
                                },
                                "timestamp": {"type": "integer"}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                },
                "/api/orders": {
                  "post": {
                    "tags": ["Orders"],
                    "summary": "Submit a new order",
                    "description": "Crea una nueva orden de compra o venta",
                    "security": [{"BasicAuth": []}],
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/json": {
                          "schema": {"$ref": "#/components/schemas/OrderRequest"}
                        }
                      }
                    },
                    "responses": {
                      "201": {
                        "description": "Orden creada exitosamente",
                        "content": {
                          "application/json": {
                            "schema": {"$ref": "#/components/schemas/ApiResponse"}
                          }
                        }
                      },
                      "400": {"description": "Solicitud inválida"},
                      "401": {"description": "No autenticado"},
                      "422": {"description": "Orden rechazada"}
                    }
                  }
                },
                "/api/orders/active": {
                  "get": {
                    "tags": ["Orders"],
                    "summary": "Get active orders",
                    "description": "Obtiene todas las órdenes activas del usuario",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {
                        "description": "Lista de órdenes activas",
                        "content": {
                          "application/json": {
                            "schema": {"$ref": "#/components/schemas/ApiResponse"}
                          }
                        }
                      },
                      "401": {"description": "No autenticado"}
                    }
                  }
                },
                "/api/orders/{clOrdID}": {
                  "get": {
                    "tags": ["Orders"],
                    "summary": "Get order by ID",
                    "description": "Obtiene una orden específica por su ID",
                    "security": [{"BasicAuth": []}],
                    "parameters": [
                      {
                        "name": "clOrdID",
                        "in": "path",
                        "required": true,
                        "schema": {"type": "string"},
                        "description": "ID de la orden"
                      }
                    ],
                    "responses": {
                      "200": {"description": "Orden encontrada"},
                      "401": {"description": "No autenticado"},
                      "404": {"description": "Orden no encontrada"}
                    }
                  },
                  "delete": {
                    "tags": ["Orders"],
                    "summary": "Cancel order",
                    "description": "Cancela una orden activa",
                    "security": [{"BasicAuth": []}],
                    "parameters": [
                      {
                        "name": "clOrdID",
                        "in": "path",
                        "required": true,
                        "schema": {"type": "string"},
                        "description": "ID de la orden a cancelar"
                      }
                    ],
                    "responses": {
                      "200": {"description": "Orden cancelada"},
                      "401": {"description": "No autenticado"},
                      "404": {"description": "Orden no encontrada"}
                    }
                  }
                },
                "/api/positions": {
                  "get": {
                    "tags": ["Positions"],
                    "summary": "Get all positions",
                    "description": "Obtiene todas las posiciones del usuario",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {
                        "description": "Lista de posiciones",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "success": {"type": "boolean"},
                                "data": {
                                  "type": "array",
                                  "items": {"$ref": "#/components/schemas/Position"}
                                }
                              }
                            }
                          }
                        }
                      },
                      "401": {"description": "No autenticado"}
                    }
                  }
                },
                "/api/positions/{symbol}": {
                  "get": {
                    "tags": ["Positions"],
                    "summary": "Get position by symbol",
                    "description": "Obtiene la posición del usuario para un símbolo específico",
                    "security": [{"BasicAuth": []}],
                    "parameters": [
                      {
                        "name": "symbol",
                        "in": "path",
                        "required": true,
                        "schema": {"type": "string"},
                        "description": "Símbolo del instrumento"
                      }
                    ],
                    "responses": {
                      "200": {"description": "Posición encontrada"},
                      "401": {"description": "No autenticado"},
                      "404": {"description": "Posición no encontrada"}
                    }
                  }
                },
                "/api/trades/recent": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "Get recent trades",
                    "description": "Obtiene los trades recientes del mercado",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {"description": "Lista de trades recientes"},
                      "401": {"description": "No autenticado"}
                    }
                  }
                },
                "/api/trades/my": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "Get user trades",
                    "description": "Obtiene todos los trades del usuario",
                    "security": [{"BasicAuth": []}],
                    "responses": {
                      "200": {
                        "description": "Lista de trades del usuario",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "success": {"type": "boolean"},
                                "data": {
                                  "type": "array",
                                  "items": {"$ref": "#/components/schemas/Trade"}
                                }
                              }
                            }
                          }
                        }
                      },
                      "401": {"description": "No autenticado"}
                    }
                  }
                },
                "/api/trades/symbol/{symbol}": {
                  "get": {
                    "tags": ["Trades"],
                    "summary": "Get trades by symbol",
                    "description": "Obtiene los trades para un símbolo específico",
                    "security": [{"BasicAuth": []}],
                    "parameters": [
                      {
                        "name": "symbol",
                        "in": "path",
                        "required": true,
                        "schema": {"type": "string"},
                        "description": "Símbolo del instrumento"
                      }
                    ],
                    "responses": {
                      "200": {"description": "Lista de trades del símbolo"},
                      "401": {"description": "No autenticado"}
                    }
                  }
                }
              },
              "tags": [
                {"name": "System", "description": "Endpoints del sistema"},
                {"name": "Market Data", "description": "Datos de mercado"},
                {"name": "Orders", "description": "Gestión de órdenes"},
                {"name": "Positions", "description": "Posiciones del usuario"},
                {"name": "Trades", "description": "Historial de trades"}
              ]
            }
            """, port);
        
        ctx.contentType("application/json");
        ctx.result(spec);
    }
}
