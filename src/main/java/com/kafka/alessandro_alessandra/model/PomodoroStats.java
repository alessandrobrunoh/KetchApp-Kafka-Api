package com.kafka.alessandro_alessandra.model;


public record PomodoroStats(int totalTomatoes, long totalStudyMinutes, long totalPauseMinutes, long avgPomodoro,
                            long maxPomodoro, long minPomodoro) {
}
