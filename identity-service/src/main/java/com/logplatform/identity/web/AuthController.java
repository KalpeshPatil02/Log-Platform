package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.LoginRequest;
import com.logplatform.identity.dto.Dtos.TokenResponse;
import com.logplatform.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }
}
