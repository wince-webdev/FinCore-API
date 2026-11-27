package com.wince.fincore.payment.application;

import java.util.UUID;

public interface ExternalPaymentProvider {
    enum Result { SUCCESS, FAILURE, TIMEOUT }
    Result process(UUID transactionId);
}
