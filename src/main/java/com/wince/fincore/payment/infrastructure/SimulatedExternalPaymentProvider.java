package com.wince.fincore.payment.infrastructure;

import com.wince.fincore.payment.application.ExternalPaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class SimulatedExternalPaymentProvider implements ExternalPaymentProvider {
    private final String mode;
    private final long latencyMs;

    public SimulatedExternalPaymentProvider(
            @Value("${fincore.external.mode:random}") String mode,
            @Value("${fincore.external.latency-ms:0}") long latencyMs) {
        this.mode = mode;
        this.latencyMs = latencyMs;
    }

    @Override
    public Result process(UUID transactionId) {
        if (latencyMs > 0) {
            try { Thread.sleep(latencyMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return switch (mode) {
            case "success" -> Result.SUCCESS;
            case "failure" -> Result.FAILURE;
            case "timeout" -> Result.TIMEOUT;
            default -> {
                double r = Math.random();
                if (r < 0.85) yield Result.SUCCESS;
                if (r < 0.95) yield Result.FAILURE;
                yield Result.TIMEOUT;
            }
        };
    }
}
