package com.saafhawa.api;

import com.saafhawa.api.key.ApiKeyService;
import com.saafhawa.common.ApiException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Self-service API-key signup (FR-6.1). */
@RestController
public class ApiKeyController {

    private final ApiKeyService keyService;

    public ApiKeyController(ApiKeyService keyService) {
        this.keyService = keyService;
    }

    public record SignupRequest(@NotBlank @Email String email) {
    }

    @PostMapping("/v1/keys")
    public Map<String, String> signup(@RequestBody SignupRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw ApiException.badRequest("email is required");
        }
        String key = keyService.signup(request.email());
        return Map.of(
                "apiKey", key,
                "tier", "KEYED",
                "message", "Store this key now; it is shown only once. Send it as the X-API-Key header.");
    }
}

// empty commit