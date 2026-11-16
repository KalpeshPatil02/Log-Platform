package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.ApiKeyValidationRequest;
import com.logplatform.identity.dto.Dtos.ApiKeyValidationResponse;
import com.logplatform.identity.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Service-to-service only; guarded by X-Internal-Token (InternalTokenFilter). Never route this through the gateway. */
@RestController
@RequestMapping("/internal/api-keys")
public class InternalApiKeyController {
    private final ApiKeyService apiKeys;

    public InternalApiKeyController(ApiKeyService apiKeys) { this.apiKeys = apiKeys; }

    @PostMapping("/validate")
    public ApiKeyValidationResponse validate(@Valid @RequestBody ApiKeyValidationRequest req) {
        return apiKeys.validate(req.apiKey());
    }
}
