package com.kafka.alessandro_alessandra.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class EmailSchedulerService {

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private EmailServices emailServices;

    public void scheduleEmail(
        String recipient,
        String subject,
        String body,
        LocalDateTime sendTime
    ) {
        LocalDateTime now = LocalDateTime.now();
        // Non schedula mail se l'orario è già passato o troppo vicino (meno di 1 minuto)
        if (sendTime.isBefore(now.plusMinutes(1))) {
            System.out.println(
                "Mail non schedulata: orario troppo vicino o già passato (" +
                sendTime +
                ")"
            );
            return;
        }
        Runnable emailTask = () -> {
            try {
                emailServices.sendHtmlEmail(recipient, subject, body);
            } catch (Exception e) {
                System.err.println(
                    "Failed to send scheduled email: " + e.getMessage()
                );
            }
        };
        Instant triggerInstant = sendTime
            .atZone(ZoneId.systemDefault())
            .toInstant();
        System.out.println("a quest'ora ti arriva una mail: " + sendTime);
        taskScheduler.schedule(emailTask, triggerInstant);
    }
}
