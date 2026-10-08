package com.pitchmap.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogJpaRepository extends JpaRepository<AdminAuditLog, Long> {}
