package com.saafhawa.qc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QcConfigRepository extends JpaRepository<QcConfigEntity, QcConfigEntity.Key> {

    List<QcConfigEntity> findByActiveTrue();
}
