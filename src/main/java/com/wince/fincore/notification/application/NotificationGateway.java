package com.wince.fincore.notification.application;

public interface NotificationGateway {
    void send(String to, String message);
}
