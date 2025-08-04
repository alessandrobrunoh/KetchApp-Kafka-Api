package com.kafka.alessandro_alessandra.kafka;

import ch.qos.logback.core.util.Duration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.alessandro_alessandra.kafka.KafkaConsumer.PomodoroMailInfo;
import com.kafka.alessandro_alessandra.model.MessageRequest;
import com.kafka.alessandro_alessandra.model.MessageRequest.Subject;
import com.kafka.alessandro_alessandra.model.MessageRequest.Subject.Tomato;
import com.kafka.alessandro_alessandra.model.PomodoroStats;
import com.kafka.alessandro_alessandra.model.PomodoroSubjectStats;
import com.kafka.alessandro_alessandra.model.TomatoStats;
import com.kafka.alessandro_alessandra.services.EmailSchedulerService;
import com.kafka.alessandro_alessandra.services.EmailServices;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(
        KafkaConsumer.class
    );

    private static final String EMAIL_PREFIX = "EMAIL:";
    private static final String DATA_SEPARATOR = "|DATA:";

    // EMAIL:alessandro.brunoh@gmail.com|DATA:{json....}

    @Value("${app.mail.default-recipient}")
    private String defaultRecipient;

    @Autowired
    private EmailServices emailService;

    @Autowired
    private EmailSchedulerService emailSchedulerService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Consumes messages from the configured Kafka topic.
     * Determines if the message is specially formatted or regular,
     * then processes and sends an email accordingly.
     *
     * @param message the Kafka message payload
     */
    @KafkaListener(
        topics = "${app.kafka.topic.mail-service}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
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

    /**
     * Checks if the given message is in the special formatted style expected by the consumer.
     * A formatted message starts with the EMAIL_PREFIX and contains the DATA_SEPARATOR.
     *
     * @param message the Kafka message to check
     * @return true if the message is formatted, false otherwise
     */
    private boolean isFormattedMessage(String message) {
        return (
            message != null &&
            message.startsWith(EMAIL_PREFIX) &&
            message.contains(DATA_SEPARATOR)
        );
    }

    /**
     * Processes a specially formatted Kafka message, extracts the recipient email and JSON data,
     * deserializes the JSON into a MessageRequest, formats an HTML email body, and sends the email.
     * Handles JSON processing and email sending exceptions.
     *
     * @param message the formatted Kafka message containing recipient and data
     */
    private void processFormattedMessage(String message) {
        try {
            int emailEndIndex = message.indexOf(DATA_SEPARATOR);
            String email = message.substring(
                EMAIL_PREFIX.length(),
                emailEndIndex
            );
            String jsonData = message.substring(
                emailEndIndex + DATA_SEPARATOR.length()
            );

            MessageRequest messageRequest = objectMapper.readValue(
                jsonData,
                MessageRequest.class
            );

            // Estrai tutti i pomodori (startAt) di tutti i soggetti
            List<PomodoroMailInfo> allTomatoes = Optional.ofNullable(
                messageRequest.getSubjects()
            )
                .stream()
                .flatMap(List::stream)
                .filter(
                    subject ->
                        subject.getTomatoes() != null &&
                        !subject.getTomatoes().isEmpty()
                )
                .flatMap(subject ->
                    subject
                        .getTomatoes()
                        .stream()
                        .filter(tomato -> tomato.getStartAt() != null)
                        .map(tomato ->
                            new PomodoroMailInfo(
                                email,
                                subject.getName(),
                                tomato.getStartAt() != null
                                    ? tomato.getStartAt().toLocalDateTime()
                                    : null
                            )
                        )
                )
                .sorted(Comparator.comparing(PomodoroMailInfo::startTime))
                .collect(Collectors.toList());

            // DEBUG: stampa tutti i tomatoes ordinati
            System.out.println("Tutti i tomatoes ordinati:");
            for (PomodoroMailInfo t : allTomatoes) {
                System.out.println(t.startTime());
            }

            // Raggruppa i pomodori in sessioni (pausa > 30 minuti)
            List<PomodoroMailInfo> sessionStarts = new ArrayList<>();
            long sessionGapMinutes = 30;
            PomodoroMailInfo lastTomato = null;
            for (PomodoroMailInfo tomato : allTomatoes) {
                if (
                    lastTomato == null ||
                    ChronoUnit.MINUTES.between(
                        lastTomato.startTime(),
                        tomato.startTime()
                    ) >
                    sessionGapMinutes
                ) {
                    sessionStarts.add(tomato); // nuovo inizio sessione
                }
                lastTomato = tomato;
            }

            // DEBUG: stampa gli inizi delle sessioni
            System.out.println("Inizi delle sessioni:");
            for (PomodoroMailInfo s : sessionStarts) {
                System.out.println(s.startTime());
            }
            // DEBUG: stampa orario attuale
            System.out.println(
                "Orario attuale: " + java.time.LocalDateTime.now()
            );


            // Invia la mail di riepilogo 15 minuti prima dell’inizio di ogni sessione
            for (PomodoroMailInfo sessionStart : sessionStarts) {
                LocalDateTime sendTime = sessionStart
                    .startTime()
                    .minusMinutes(15);
                String subject = "Tra 15 minuti, alle ore " +
                        sessionStart.startTime().toLocalTime() +
                        ", inizia la sessione di studio per " +
                        sessionStart.subjectName();
                String htmlBody = formatHtmlEmailBody(messageRequest);
                System.out.println(
                    "Schedulo mail di riepilogo per " +
                    sessionStart.email() +
                    " alle " +
                    sendTime
                );
                log.info(
                    "Schedulo mail di riepilogo per {} alle {}",
                    sessionStart.email(),
                    sendTime
                );
                emailSchedulerService.scheduleEmail(
                    sessionStart.email(),
                    subject,
                    htmlBody,
                    sendTime
                );
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON data: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes a regular (non-formatted) Kafka message by sending it as an HTML email
     * to the default recipient. The message content is escaped and included in the email body.
     *
     * @param message the Kafka message to process and send
     */
    private void processRegularMessage(String message) {
        try {
            String subject = "Kafka Message Received";
            String htmlBody =
                "<p>A message has been received on Kafka:</p><pre style='background:#f4f4f4;padding:10px;border-radius:5px;'>" +
                escapeHtml(message) +
                "</pre>";

            emailService.sendHtmlEmail(defaultRecipient, subject, htmlBody);
            log.info(
                "Regular email sent to default recipient: {}",
                defaultRecipient
            );
        } catch (MessagingException e) {
            log.error("Error sending HTML email: {}", e.getMessage(), e);
        }
    }

    /**
     * Formats the HTML email body using the provided MessageRequest.
     * Loads the HTML template and CSS from resources, builds the tomato and stats HTML blocks,
     * and replaces placeholders in the template with the generated content.
     *
     * @param messageRequest the MessageRequest containing session and subject data
     * @return the formatted HTML email body as a String
     */
    private String formatHtmlEmailBody(MessageRequest messageRequest) {
        PomodoroStats stats = calculatePomodoroStats(messageRequest);
        String htmlTemplate = loadResourceFile(
            "/templates/email_template.html"
        );
        String css = loadResourceFile("/templates/email_styles.css");
        String tomatoHtml = buildTomatoHtml(messageRequest);
        String statsHtml = buildStatsHtml(stats);
        return htmlTemplate
            .replace("${CSS}", css)
            .replace("${TOMATO_BLOCK}", tomatoHtml)
            .replace("${STATS_BLOCK}", statsHtml);
    }

    /**
     * Calculates aggregated Pomodoro statistics from the given MessageRequest.
     * Iterates over all subjects and their Pomodoro sessions to sum up total tomatoes,
     * total study minutes, total pause minutes, and determines the average, maximum,
     * and minimum Pomodoro durations.
     *
     * @param messageRequest the MessageRequest containing subjects and Pomodoro sessions
     * @return PomodoroStats object with aggregated statistics
     */
    private PomodoroStats calculatePomodoroStats(
        MessageRequest messageRequest
    ) {
        int totalTomatoes = 0;
        long totalStudyMinutes = 0;
        long totalPauseMinutes = 0;
        long maxPomodoro = 0;
        long minPomodoro = Long.MAX_VALUE;
        if (messageRequest.getSubjects() != null) {
            for (Subject subject : messageRequest.getSubjects()) {
                PomodoroSubjectStats subjectStats = calculateSubjectStats(
                    subject
                );
                totalTomatoes += subjectStats.tomatoesCount();
                totalStudyMinutes += subjectStats.studyMinutes();
                totalPauseMinutes += subjectStats.pauseMinutes();
                if (subjectStats.maxPomodoro() > maxPomodoro) maxPomodoro =
                    subjectStats.maxPomodoro();
                if (subjectStats.minPomodoro() < minPomodoro) minPomodoro =
                    subjectStats.minPomodoro();
            }
        }
        if (minPomodoro == Long.MAX_VALUE) minPomodoro = 0;
        long avgPomodoro = totalTomatoes > 0
            ? totalStudyMinutes / totalTomatoes
            : 0;
        return new PomodoroStats(
            totalTomatoes,
            totalStudyMinutes,
            totalPauseMinutes,
            avgPomodoro,
            maxPomodoro,
            minPomodoro
        );
    }

    /**
     * Calculates statistics for a given subject, including the number of valid Pomodoro (Tomato) sessions,
     * total study minutes, total pause minutes, maximum and minimum Pomodoro durations.
     *
     * @param subject the Subject object containing Pomodoro sessions
     * @return PomodoroSubjectStats with aggregated statistics for the subject
     */
    private PomodoroSubjectStats calculateSubjectStats(Subject subject) {
        int tomatoesCount = 0;
        long studyMinutes = 0,
            pauseMinutes = 0,
            maxPomodoro = 0,
            minPomodoro = Long.MAX_VALUE;

        if (subject.getTomatoes() != null) {
            for (MessageRequest.Subject.Tomato tomato : subject.getTomatoes()) {
                TomatoStats stats = calculateTomatoStats(tomato);
                if (stats.isValid()) {
                    tomatoesCount++;
                    studyMinutes += stats.getTotalStudyMinutes();
                    pauseMinutes += stats.getTotalPauseMinutes();
                    maxPomodoro = Math.max(
                        maxPomodoro,
                        stats.getTotalStudyMinutes()
                    );
                    minPomodoro = Math.min(
                        minPomodoro,
                        stats.getTotalStudyMinutes()
                    );
                }
            }
        }
        if (minPomodoro == Long.MAX_VALUE) minPomodoro = 0;
        return new PomodoroSubjectStats(
            tomatoesCount,
            studyMinutes,
            pauseMinutes,
            maxPomodoro,
            minPomodoro
        );
    }

    /**
     * Calculates the study and pause minutes for a single Tomato (Pomodoro) session.
     * Returns a TomatoStats object containing the total study and pause minutes.
     * If start or end time is missing, returns zeroed stats.
     *
     * @param tomato the Tomato object representing a Pomodoro session
     * @return TomatoStats containing study and pause minutes
     */
    private TomatoStats calculateTomatoStats(Tomato tomato) {
        if (tomato.getStartAt() != null && tomato.getEndAt() != null) {
            long studyMinutes = java.time.Duration.between(
                tomato.getStartAt(),
                tomato.getEndAt()
            ).toMinutes();
            long pauseMinutes = 0;
            if (tomato.getPauseEndAt() != null) {
                pauseMinutes = java.time.Duration.between(
                    tomato.getStartAt(),
                    tomato.getPauseEndAt()
                ).toMinutes();
            }
            return new TomatoStats(studyMinutes, pauseMinutes);
        }
        return new TomatoStats(0, 0);
    }

    /**
     * Builds the HTML block representing all Pomodoro (Tomato) sessions for each subject
     * in the given MessageRequest. If no subjects or tomatoes are present, a placeholder
     * message is returned.
     *
     * @param messageRequest the MessageRequest containing subjects and their Pomodoro sessions
     * @return HTML string representing all Pomodoro sessions for the email body
     */
    private String buildTomatoHtml(MessageRequest messageRequest) {
        StringBuilder html = new StringBuilder();
        if (
            messageRequest.getSubjects() != null &&
            !messageRequest.getSubjects().isEmpty()
        ) {
            for (Subject subject : messageRequest.getSubjects()) {
                html.append(buildSubjectHtml(subject));
            }
        } else {
            html.append("<p>No Pomodoro sessions found.</p>");
        }
        return html.toString();
    }

    /**
     * Builds the HTML block displaying Pomodoro session statistics.
     * Formats total Pomodoros, study time, pause time, average, longest, and shortest Pomodoro durations.
     *
     * @param stats the PomodoroStats object containing session statistics
     * @return HTML string representing the statistics block
     */
    private String buildStatsHtml(PomodoroStats stats) {
        return (
            "<div class='stats-card'>" +
            "<h3 style='color:#444;font-size:18px;margin-top:0;margin-bottom:15px;'>Session Statistics</h3>" +
            "<div class='stats-row'>" +
            "<div class='stat'><p class='stat-label'>Total Pomodoros</p><p class='stat-value'>" +
            stats.totalTomatoes() +
            "</p></div>" +
            "<div class='stat'><p class='stat-label'>Study Time</p><p class='stat-value'>" +
            stats.totalStudyMinutes() +
            " min</p></div>" +
            "<div class='stat'><p class='stat-label'>Pause Time</p><p class='stat-value'>" +
            stats.totalPauseMinutes() +
            " min</p></div>" +
            "<div class='stat'><p class='stat-label'>Avg Pomodoro</p><p class='stat-value'>" +
            stats.avgPomodoro() +
            " min</p></div>" +
            "<div class='stat'><p class='stat-label'>Longest Pomodoro</p><p class='stat-value'>" +
            stats.maxPomodoro() +
            " min</p></div>" +
            "<div class='stat'><p class='stat-label'>Shortest Pomodoro</p><p class='stat-value'>" +
            stats.minPomodoro() +
            " min</p></div>" +
            "</div></div>"
        );
    }

    /**
     * Builds the HTML representation for a subject, including its name and all associated Pomodoro (Tomato) sessions.
     * If the subject has tomatoes, each is rendered using buildSingleTomatoHtml.
     *
     * @param subject the Subject object containing Pomodoro sessions
     * @return HTML string representing the subject and its Pomodoro sessions
     */
    private String buildSubjectHtml(Subject subject) {
        StringBuilder html = new StringBuilder();
        html.append("<div class='subject-block'>");
        html
            .append("<h3 class='subject-title'>")
            .append(escapeHtml(subject.getName()))
            .append("</h3>");
        if (subject.getTomatoes() != null && !subject.getTomatoes().isEmpty()) {
            html.append("<div class='tomato-list'>");
            int tomatoCount = 0;
            for (Tomato tomato : subject.getTomatoes()) {
                tomatoCount++;
                html.append(buildSingleTomatoHtml(tomato, tomatoCount));
            }
            html.append("</div>");
        }
        html.append("</div>");
        return html.toString();
    }

    /**
     * Builds the HTML representation for a single Pomodoro (Tomato) session.
     * Includes session number, date, start and end times, duration, and pause information if available.
     *
     * @param tomato      the Tomato object representing a Pomodoro session
     * @param tomatoCount the sequential number of the Pomodoro in the subject
     * @return HTML string for the single Pomodoro session
     */
    private String buildSingleTomatoHtml(Tomato tomato, int tomatoCount) {
        java.time.format.DateTimeFormatter dateFormatter =
            java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy");
        java.time.format.DateTimeFormatter timeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        StringBuilder html = new StringBuilder();
        html.append("<div class='tomato-card'>");
        html.append("<p><b>Pomodoro #").append(tomatoCount).append("</b></p>");
        if (tomato.getStartAt() != null && tomato.getEndAt() != null) {
            String date = tomato.getStartAt().format(dateFormatter);
            String startTime = tomato.getStartAt().format(timeFormatter);
            String endTime = tomato.getEndAt().format(timeFormatter);
            long minutes = java.time.Duration.between(
                tomato.getStartAt(),
                tomato.getEndAt()
            ).toMinutes();
            html
                .append("<p>")
                .append(date)
                .append(" | ")
                .append(startTime)
                .append(" - ")
                .append(endTime)
                .append("</p>");
            html
                .append("<p>Duration: <b>")
                .append(minutes)
                .append(" min</b></p>");
            if (tomato.getPauseEndAt() != null) {
                String pauseEndTime = tomato
                    .getPauseEndAt()
                    .format(timeFormatter);
                long pauseMinutes = java.time.Duration.between(
                    tomato.getStartAt(),
                    tomato.getPauseEndAt()
                ).toMinutes();
                html
                    .append("<p>Pause until: ")
                    .append(pauseEndTime)
                    .append(" (")
                    .append(pauseMinutes)
                    .append(" min)</p>");
            }
        }
        html.append("</div>");
        return html.toString();
    }

    /**
     * Loads the contents of a resource file from the classpath as a String.
     * Returns an empty string if the resource is not found or an error occurs.
     *
     * @param path the classpath location of the resource file
     * @return the file contents as a String, or an empty string if not found/error
     */
    private String loadResourceFile(String path) {
        try (java.io.InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return "";
            return new String(
                is.readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Escapes special HTML characters in the input string to prevent HTML injection.
     * Converts &, <, >, ", and ' to their corresponding HTML entities.
     *
     * @param input the string to escape
     * @return the escaped string safe for HTML rendering
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    // Classe record di supporto per migliorare la leggibilità
    record PomodoroMailInfo(
        String email,
        String subjectName,
        LocalDateTime startTime
    ) {}
}
