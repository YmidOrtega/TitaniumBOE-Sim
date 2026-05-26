package com.boe.simulator.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterRequest(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password
) {}
