package com.saafhawa.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationAliasRepository extends JpaRepository<StationAlias, Long> {

    Optional<StationAlias> findBySourceAndSourceKey(String source, String sourceKey);
}
