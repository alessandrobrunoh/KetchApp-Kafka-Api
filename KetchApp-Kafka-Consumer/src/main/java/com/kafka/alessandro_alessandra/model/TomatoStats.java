package com.kafka.alessandro_alessandra.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TomatoStats {
    private int totalTomatoes;
    private long totalStudyMinutes;
    private long totalPauseMinutes;
    private long avgPomodoro;
    private long maxPomodoro;
    private long minPomodoro;

    public TomatoStats(long studyMinutes, long pauseMinutes) {
        this.totalTomatoes = 0;
        this.totalStudyMinutes = studyMinutes;
        this.totalPauseMinutes = pauseMinutes;
        this.avgPomodoro = 0;
        this.maxPomodoro = 0;
        this.minPomodoro = 0;
    }

    public boolean isValid() {
        return totalTomatoes >= 0 && totalStudyMinutes >= 0 && totalPauseMinutes >= 0 &&
               avgPomodoro >= 0 && maxPomodoro >= 0 && minPomodoro >= 0;
    }
}