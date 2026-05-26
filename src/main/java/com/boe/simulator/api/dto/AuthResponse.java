package com.boe.simulator.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(
        @JsonProperty("username") String username,
        @JsonProperty("message") String message
) {}
