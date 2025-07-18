package com.kafka.alessandro_alessandra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.alessandro_alessandra.model.MessageRequest;
import com.kafka.alessandro_alessandra.model.MessageRequest.Subject;
import com.kafka.alessandro_alessandra.model.MessageRequest.Subject.Tomato;
import com.kafka.alessandro_alessandra.services.EmailServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for consuming messages from Kafka and sending emails.
 */
@Service
@Slf4j
public class KafkaConsumer {

    private static final String EMAIL_PREFIX = "EMAIL:";
    private static final String DATA_SEPARATOR = "|DATA:";

    @Value("${app.mail.default-recipient}")
    private String defaultRecipient;

    @Value("${app.kafka.topic.mail-service}")
    private String mailServiceTopic;

    private final EmailServices emailService;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaConsumer(EmailServices emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topic.mail-service}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        log.info("Consumed message: {}", message);

        try {
            if (isFormattedMessage(message)) {
                processFormattedMessage(message);
            } else {
                processRegularMessage(message);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
        }
    }

    private boolean isFormattedMessage(String message) {
        return message != null && message.startsWith(EMAIL_PREFIX) && message.contains(DATA_SEPARATOR);
    }

    private void processFormattedMessage(String message) {
        try {
            int emailEndIndex = message.indexOf(DATA_SEPARATOR);
            String email = message.substring(EMAIL_PREFIX.length(), emailEndIndex);
            String jsonData = message.substring(emailEndIndex + DATA_SEPARATOR.length());

            MessageRequest messageRequest = objectMapper.readValue(jsonData, MessageRequest.class);

            String subject = "Today Study Session Summary";
            String htmlBody = formatHtmlEmailBody(messageRequest);

            emailService.sendHtmlEmail(email, subject, htmlBody);

            log.info("Formatted HTML email sent to: {}", email);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON data: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("Error sending HTML email: {}", e.getMessage(), e);
        }
    }

    private void processRegularMessage(String message) {
        try {
            String subject = "Kafka Message Received";
            String htmlBody = "<p>A message has been received on Kafka:</p><pre style='background:#f4f4f4;padding:10px;border-radius:5px;'>"
                + escapeHtml(message) + "</pre>";

            emailService.sendHtmlEmail(defaultRecipient, subject, htmlBody);
            log.info("Regular email sent to default recipient: {}", defaultRecipient);
        } catch (MessagingException e) {
            log.error("Error sending HTML email: {}", e.getMessage(), e);
        }
    }

    private String formatHtmlEmailBody(MessageRequest messageRequest) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        int totalTomatoes = 0;
        long totalStudyMinutes = 0;
        long totalPauseMinutes = 0;

        if (messageRequest.getSubjects() != null) {
            for (Subject subject : messageRequest.getSubjects()) {
                if (subject.getTomatoes() != null) {
                    totalTomatoes += subject.getTomatoes().size();

                    for (Tomato tomato : subject.getTomatoes()) {
                        if (tomato.getStart_at() != null && tomato.getEnd_at() != null) {
                            Duration duration = Duration.between(tomato.getStart_at(), tomato.getEnd_at());
                            totalStudyMinutes += duration.toMinutes();

                            if (tomato.getPause_end_at() != null) {
                                Duration pauseDuration = Duration.between(tomato.getStart_at(), tomato.getPause_end_at());
                                totalPauseMinutes += pauseDuration.toMinutes();
                            }
                        }
                    }
                }
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<style>\n" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; background: #f6f7fb; color: #222; }\n" +
                ".glass-container { max-width: 700px; margin: 40px auto; background: linear-gradient(135deg, rgba(255,255,255,0.92) 0%, rgba(255,255,255,0.45) 100%); border-radius: 28px; backdrop-filter: blur(22px); -webkit-backdrop-filter: blur(22px); border: 2px solid rgba(255,255,255,0.45); box-shadow: 0 16px 48px rgba(0,0,0,0.15); padding: 48px 36px 36px 36px; position: relative; overflow: hidden; transition: box-shadow 0.3s; }\n" +
                ".glass-container:hover { box-shadow: 0 24px 64px rgba(214,40,57,0.18); }\n" +
                ".header { background: linear-gradient(135deg, #ff6b6b 0%, #d62839 100%); border-radius: 18px; padding: 32px 28px 24px 28px; margin-bottom: 36px; color: white; box-shadow: 0 8px 32px rgba(214,40,57,0.20); backdrop-filter: blur(3px); position: relative; z-index: 1; }\n" +
                ".header h1 { margin: 0; font-size: 36px; font-weight: 800; letter-spacing: 1.5px; text-shadow: 0 3px 12px #d6283933; }\n" +
                ".header p { margin: 14px 0 0; opacity: 0.95; font-size: 20px; }\n" +
                ".glass-card { background: rgba(255,255,255,0.88); border-radius: 18px; padding: 28px 24px 24px 24px; margin-bottom: 32px; box-shadow: 0 6px 24px rgba(0,0,0,0.09); position: relative; z-index: 1; }\n" +
                ".section-title { margin-top: 0; color: #d62839; font-size: 26px; font-weight: 700; letter-spacing: 0.5px; }\n" +
                ".subject-block { margin-bottom: 28px; }\n" +
                ".subject-title { margin: 0 0 12px; color: #d62839; font-size: 20px; font-weight: 600; }\n" +
                ".tomato-list { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 12px; }\n" +
                ".tomato-card { background: linear-gradient(135deg, rgba(255,107,107,0.22) 0%, rgba(214,40,57,0.13) 100%); border-radius: 12px; padding: 18px; flex: 1; min-width: 220px; box-shadow: 0 3px 12px rgba(214,40,57,0.08); transition: transform 0.2s; }\n" +
                ".tomato-card:hover { transform: translateY(-4px) scale(1.03); box-shadow: 0 8px 32px rgba(214,40,57,0.13); }\n" +
                ".tomato-card p { margin: 0 0 6px 0; font-size: 15px; }\n" +
                ".stats-card { background: rgba(255,255,255,0.8); border-radius: 14px; padding: 24px; margin-bottom: 24px; box-shadow: 0 6px 20px rgba(0,0,0,0.07); }\n" +
                ".stats-row { display: flex; flex-wrap: wrap; gap: 18px; }\n" +
                ".stat { flex: 1; min-width: 160px; background: linear-gradient(135deg, rgba(255,107,107,0.22) 0%, rgba(214,40,57,0.13) 100%); border-radius: 12px; padding: 18px; text-align: center; box-shadow: 0 2px 8px rgba(214,40,57,0.07); }\n" +
                ".stat-label { margin: 0; font-size: 15px; color: #666; font-weight: 500; }\n" +
                ".stat-value { margin: 7px 0 0; font-size: 28px; font-weight: 800; color: #d62839; letter-spacing: 0.5px; }\n" +
                ".footer { text-align: center; margin-top: 36px; padding-top: 24px; border-top: 1.5px solid rgba(0,0,0,0.12); color: #666; font-size: 15px; }\n" +
                ".footer strong { color: #d62839; }\n" +
                "</style>\n");
        html.append("<div class='glass-container'>");
        html.append("<div class='blob1'></div><div class='blob2'></div>");
        html.append("<div class='header'>");
        html.append("<h1>Study Session Summary</h1>");
        html.append("<p>Your productivity report from <span style='font-weight:bold;'>ketchApp</span></p>");
        html.append("</div>");
        html.append("<div class='glass-card'>");
        html.append("<h2 class='section-title'>Pomodoro Sessions</h2>");
        if (messageRequest.getSubjects() != null && !messageRequest.getSubjects().isEmpty()) {
            for (Subject subject : messageRequest.getSubjects()) {
                html.append("<div class='subject-block'>");
                html.append("<h3 class='subject-title'>" + escapeHtml(subject.getName()) + "</h3>");
                if (subject.getTomatoes() != null && !subject.getTomatoes().isEmpty()) {
                    html.append("<div class='tomato-list'>");
                    int tomatoCount = 0;
                    for (Tomato tomato : subject.getTomatoes()) {
                        tomatoCount++;
                        html.append("<div class='tomato-card'>");
                        html.append("<p><b>Pomodoro #" + tomatoCount + "</b></p>");
                        if (tomato.getStart_at() != null && tomato.getEnd_at() != null) {
                            String date = tomato.getStart_at().format(dateFormatter);
                            String startTime = tomato.getStart_at().format(timeFormatter);
                            String endTime = tomato.getEnd_at().format(timeFormatter);
                            Duration duration = Duration.between(tomato.getStart_at(), tomato.getEnd_at());
                            long minutes = duration.toMinutes();
                            html.append("<p>" + date + " | " + startTime + " - " + endTime + "</p>");
                            html.append("<p>Duration: <b>" + minutes + " min</b></p>");
                            if (tomato.getPause_end_at() != null) {
                                String pauseEndTime = tomato.getPause_end_at().format(timeFormatter);
                                Duration pauseDuration = Duration.between(tomato.getStart_at(), tomato.getPause_end_at());
                                long pauseMinutes = pauseDuration.toMinutes();
                                html.append("<p>Pause until: " + pauseEndTime + " (" + pauseMinutes + " min)</p>");
                            }
                        }
                        html.append("</div>");
                    }
                    html.append("</div>");
                }
                html.append("</div>");
            }
        } else {
            html.append("<p>No Pomodoro sessions found.</p>");
        }
        html.append("</div>");
        html.append("<div class='stats-card'>");
        html.append("<h3 style='color:#444;font-size:18px;margin-top:0;margin-bottom:15px;'>Session Statistics</h3>");
        html.append("<div class='stats-row'>");
        html.append("<div class='stat'><p class='stat-label'>Total Pomodoros</p><p class='stat-value'>" + totalTomatoes + "</p></div>");
        html.append("<div class='stat'><p class='stat-label'>Study Time</p><p class='stat-value'>" + totalStudyMinutes + " min</p></div>");
        html.append("<div class='stat'><p class='stat-label'>Pause Time</p><p class='stat-value'>" + totalPauseMinutes + " min</p></div>");
        long avgPomodoro = totalTomatoes > 0 ? totalStudyMinutes / totalTomatoes : 0;
        long maxPomodoro = 0;
        long minPomodoro = Long.MAX_VALUE;
        if (messageRequest.getSubjects() != null) {
            for (Subject subject : messageRequest.getSubjects()) {
                if (subject.getTomatoes() != null) {
                    for (Tomato tomato : subject.getTomatoes()) {
                        if (tomato.getStart_at() != null && tomato.getEnd_at() != null) {
                            long min = Duration.between(tomato.getStart_at(), tomato.getEnd_at()).toMinutes();
                            if (min > maxPomodoro) maxPomodoro = min;
                            if (min < minPomodoro) minPomodoro = min;
                        }
                    }
                }
            }
        }
        if (minPomodoro == Long.MAX_VALUE) minPomodoro = 0;
        html.append("<div class='stat'><p class='stat-label'>Avg Pomodoro</p><p class='stat-value'>" + avgPomodoro + " min</p></div>");
        html.append("<div class='stat'><p class='stat-label'>Longest Pomodoro</p><p class='stat-value'>" + maxPomodoro + " min</p></div>");
        html.append("<div class='stat'><p class='stat-label'>Shortest Pomodoro</p><p class='stat-value'>" + minPomodoro + " min</p></div>");
        html.append("</div></div>");
        html.append("<div class='footer'>");
        html.append("This plan was created using <strong>KetchApp</strong><br>");
        html.append("Developed by <strong>Alessandro Bruno</strong> &amp; <strong>Alessandra Di Bella</strong>");
        html.append("</div>");
        html.append("</div>");
        return html.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}

