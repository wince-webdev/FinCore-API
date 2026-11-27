package com.wince.fincore.compliance.application;

import com.wince.fincore.ledger.application.TransactionCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

@Component
public class AmlTransactionListener {
    private final AmlService amlService;

    public AmlTransactionListener(AmlService amlService) { this.amlService = amlService; }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        amlService.evaluate(event.transactionId());
    }
}
