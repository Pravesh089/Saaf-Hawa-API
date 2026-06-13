package com.saafhawa.ingest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    Page<IngestionRun> findBySourceOrderByStartedAtDesc(String source, Pageable pageable);

    Optional<IngestionRun> findFirstBySourceAndOutcomeOrderByStartedAtDesc(String source, String outcome);

    /** Most recent runs per the given source, newest first; used for consecutive-failure checks. */
    List<IngestionRun> findTop10BySourceOrderByStartedAtDesc(String source);

    Optional<IngestionRun> findFirstBySourceOrderByStartedAtDesc(String source);
}
