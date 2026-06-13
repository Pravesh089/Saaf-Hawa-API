package com.saafhawa.api.key;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Issues and resolves API keys (FR-6.1). Keys are shown once at signup; only the hash is stored. */
@Service
public class ApiKeyService {

    private final ApiClientRepository repo;

    public ApiKeyService(ApiClientRepository repo) {
        this.repo = repo;
    }

    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Self-service signup: returns the raw key (shown once). */
    @Transactional
    public String signup(String email) {
        String rawKey = "sh_" + UUID.randomUUID().toString().replace("-", "");
        repo.save(new ApiClient(sha256(rawKey), email, "KEYED", null));
        return rawKey;
    }

    @Transactional(readOnly = true)
    public Optional<ApiClient> resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return repo.findByKeyHash(sha256(rawKey)).filter(c -> !c.isRevoked());
    }
}
