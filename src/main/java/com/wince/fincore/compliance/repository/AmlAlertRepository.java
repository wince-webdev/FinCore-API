package com.wince.fincore.compliance.repository;

import com.wince.fincore.compliance.domain.AmlAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {
    List<AmlAlert> findByStatus(String status);
    List<AmlAlert> findByCustomerId(UUID customerId);
    List<AmlAlert> findByRuleCode(String ruleCode);

    @Query("SELECT a FROM AmlAlert a WHERE (:status IS NULL OR a.status = :status) AND (:ruleCode IS NULL OR a.ruleCode = :ruleCode)")
    List<AmlAlert> search(@Param("status") String status, @Param("ruleCode") String ruleCode);
}
