package com.kafka.alessandro_alessandra.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class EmailSchedulerService {
    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private EmailServices emailServices;

    public void scheduleEmail(String recipient, String subject, String body, LocalDateTime sendTime) {
        Runnable emailTask = () -> {
            try {
                emailServices.sendHtmlEmail(recipient, subject, body);
            } catch (Exception e) {
                System.err.println("Failed to send scheduled email: " + e.getMessage());
            }
        };
        Instant triggerInstant = sendTime.atZone(ZoneId.systemDefault()).toInstant();
        taskScheduler.schedule(emailTask, triggerInstant);
    }
}
