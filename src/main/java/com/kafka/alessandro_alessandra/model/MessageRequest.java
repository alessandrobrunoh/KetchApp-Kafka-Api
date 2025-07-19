package com.kafka.alessandro_alessandra.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        private LocalDateTime start_at;
        private LocalDateTime end_at;
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
            private LocalDateTime start_at;
            private LocalDateTime end_at;
            private LocalDateTime pause_end_at;
        }
    }
}