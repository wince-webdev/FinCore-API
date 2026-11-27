package com.wince.fincore.ledger.application;

import java.util.UUID;

public record TransactionCompletedEvent(UUID transactionId) {}
