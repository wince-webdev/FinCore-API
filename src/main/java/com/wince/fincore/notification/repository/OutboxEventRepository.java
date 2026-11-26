package com.wince.fincore.notification.repository;

import com.wince.fincore.notification.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String status, Instant now);

    @Query("SELECT o FROM OutboxEvent o WHERE o.aggregateType = :type AND o.aggregateId = :id")
    List<OutboxEvent> findByAggregate(String type, UUID id);
}
