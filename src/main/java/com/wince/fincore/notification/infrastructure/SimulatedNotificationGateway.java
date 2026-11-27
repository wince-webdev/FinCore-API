package com.wince.fincore.notification.infrastructure;

import com.wince.fincore.notification.application.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SimulatedNotificationGateway implements NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationGateway.class);
    private final Path file;

    public SimulatedNotificationGateway() {
        this.file = Path.of("notifications.log");
    }

    @Override
    public void send(String to, String message) {
        String line = "[SMS/EMAIL] to=" + to + " message=" + message;
        log.info(line);
        try {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            try (FileWriter fw = new FileWriter(file.toFile(), true)) {
                fw.write(line + System.lineSeparator());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
