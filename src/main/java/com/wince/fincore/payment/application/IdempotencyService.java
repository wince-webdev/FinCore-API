package com.wince.fincore.payment.application;

import tools.jackson.databind.ObjectMapper;
import com.wince.fincore.payment.domain.IdempotencyRecord;
import com.wince.fincore.payment.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public record BeginResult(boolean replay, boolean conflict, boolean reused,
                              IdempotencyRecord record, Integer replayStatus, String replayBody) {}

    public BeginResult begin(UUID userId, String endpoint, String key, String requestHash) {
        Optional<IdempotencyRecord> existing = repository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (rec.getExpiresAt().isBefore(clock.instant())) {
                repository.delete(rec);
            } else {
                if (!rec.getRequestHash().equals(requestHash)) {
                    return new BeginResult(false, false, true, rec, null, null);
                }
                if ("IN_PROGRESS".equals(rec.getStatus())) {
                    return new BeginResult(false, true, false, rec, null, null);
                }
                return new BeginResult(true, false, false, rec, rec.getResponseStatus(), rec.getResponseBody());
            }
        }
        IdempotencyRecord rec = new IdempotencyRecord(UUID.randomUUID(), key, userId, endpoint, requestHash,
                "IN_PROGRESS", clock.instant().plusSeconds(86400));
        try {
            repository.saveAndFlush(rec);
            return new BeginResult(false, false, false, rec, null, null);
        } catch (DataIntegrityViolationException e) {
            IdempotencyRecord rec2 = repository.findByUserIdAndIdempotencyKey(userId, key).orElseThrow();
            if (!rec2.getRequestHash().equals(requestHash)) {
                return new BeginResult(false, false, true, rec2, null, null);
            }
            if ("IN_PROGRESS".equals(rec2.getStatus())) {
                return new BeginResult(false, true, false, rec2, null, null);
            }
            return new BeginResult(true, false, false, rec2, rec2.getResponseStatus(), rec2.getResponseBody());
        }
    }

    public void complete(IdempotencyRecord record, int status, String body, UUID transactionId) {
        record.setStatus("COMPLETED");
        record.setResponseStatus(status);
        record.setResponseBody(body);
        record.setTransactionId(transactionId);
        repository.save(record);
    }

    public void fail(IdempotencyRecord record) {
        repository.delete(record);
    }

    public String hashRequest(Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return sha256(json);
        } catch (Exception e) {
            return sha256(String.valueOf(request));
        }
    }

    private String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
