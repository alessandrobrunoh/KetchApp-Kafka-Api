package com.kafka.alessandro_alessandra.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {

    @Valid
    private List<CalendarEvent> calendar;

    @Valid
    private List<Subject> subjects;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CalendarEvent {

        private String title;

        @JsonProperty("start_at")
        private LocalDateTime startAt;

        @JsonProperty("end_at")
        private LocalDateTime endAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Subject {

        private String name;
        private List<Tomato> tomatoes;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Tomato {

            @JsonProperty("start_at")
            private OffsetDateTime startAt;

            @JsonProperty("end_at")
            private OffsetDateTime endAt;

            @JsonProperty("pause_end_at")
            private OffsetDateTime pauseEndAt;
        }
    }
}
