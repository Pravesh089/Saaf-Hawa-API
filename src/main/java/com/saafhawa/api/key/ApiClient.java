package com.saafhawa.api.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** API-key holder (§4.1, FR-6). Stores only the SHA-256 hash of the key, never the raw key. */
@Entity
@Table(name = "api_client")
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key_hash")
    private String keyHash;
    private String email;
    private String tier = "KEYED";
    @Column(name = "rate_limit_override")
    private Integer rateLimitOverride;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    private boolean revoked;

    protected ApiClient() {
    }

    public ApiClient(String keyHash, String email, String tier, Integer rateLimitOverride) {
        this.keyHash = keyHash;
        this.email = email;
        this.tier = tier;
        this.rateLimitOverride = rateLimitOverride;
    }

    public Long getId() {
        return id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getEmail() {
        return email;
    }

    public String getTier() {
        return tier;
    }

    public Integer getRateLimitOverride() {
        return rateLimitOverride;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
}
