package com.wince.fincore.notification.application;

import com.wince.fincore.notification.domain.OutboxEvent;
import com.wince.fincore.notification.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxDispatcher {
    private final OutboxEventRepository repository;
    private final NotificationGateway gateway;
    private final Clock clock;

    public OutboxDispatcher(OutboxEventRepository repository, NotificationGateway gateway, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fincore.notification.dispatcher-ms:5000}")
    public void dispatch() {
        List<OutboxEvent> events = repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("NEW", clock.instant());
        for (OutboxEvent event : events) {
            try {
                process(event);
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public void process(OutboxEvent event) {
        OutboxEvent e = repository.findById(event.getId()).orElseThrow();
        if (!"NEW".equals(e.getStatus())) return;
        try {
            gateway.send("client", e.getEventType() + " " + e.getPayload());
            e.setStatus("SENT");
            e.setSentAt(clock.instant());
            repository.save(e);
        } catch (Exception ex) {
            e.setAttempts(e.getAttempts() + 1);
            if (e.getAttempts() >= 5) {
                e.setStatus("FAILED");
            } else {
                e.setNextAttemptAt(clock.instant().plusSeconds((long) Math.pow(2, e.getAttempts())));
            }
            repository.save(e);
        }
    }
}
