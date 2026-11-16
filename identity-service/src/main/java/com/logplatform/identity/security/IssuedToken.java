package com.logplatform.identity.security;

public record IssuedToken(String accessToken, long expiresInSeconds) {}
