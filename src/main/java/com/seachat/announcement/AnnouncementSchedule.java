package com.seachat.announcement;

import java.time.LocalTime;
import java.util.List;

record AnnouncementSchedule(
        boolean timesEnabled,
        List<ScheduledTime> times,
        boolean intervalEnabled,
        LocalTime startTime,
        long intervalSeconds
) {
    boolean hasValidSchedule() {
        if (intervalEnabled) {
            return startTime != null && intervalSeconds > 0L;
        }
        return timesEnabled && !times.isEmpty();
    }
}
