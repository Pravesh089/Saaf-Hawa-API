package com.saafhawa.qc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** A single versioned QC threshold (FR-3.2). */
@Entity
@Table(name = "qc_config")
@IdClass(QcConfigEntity.Key.class)
public class QcConfigEntity {

    @Id
    @Column(name = "ruleset_version")
    private String rulesetVersion;
    @Id
    private String key;
    private String value;
    private boolean active;

    protected QcConfigEntity() {
    }

    public String getRulesetVersion() {
        return rulesetVersion;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isActive() {
        return active;
    }

    public static class Key implements Serializable {
        private String rulesetVersion;
        private String key;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(rulesetVersion, k.rulesetVersion) && Objects.equals(key, k.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rulesetVersion, key);
        }
    }
}
