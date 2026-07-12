package com.seachat.announcement;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

record Announcement(
        String id,
        List<String> messages,
        AnnouncementSchedule schedule,
        String trigger
) {
    boolean shouldSend(LocalDateTime previousCheck, LocalDateTime now) {
        if (schedule.intervalEnabled()) {
            return intervalDue(previousCheck, now);
        }
        return schedule.timesEnabled() && listedTimeDue(previousCheck, now);
    }

    boolean hasScheduledActivation() {
        return schedule.hasValidSchedule();
    }

    private boolean listedTimeDue(LocalDateTime previousCheck, LocalDateTime now) {
        for (LocalDate date = previousCheck.toLocalDate(); !date.isAfter(now.toLocalDate()); date = date.plusDays(1L)) {
            for (ScheduledTime scheduledTime : schedule.times()) {
                LocalDateTime dueTime = date.atTime(scheduledTime.time());
                if (!scheduledTime.hasSeconds()) {
                    dueTime = dueTime.withSecond(0).withNano(0);
                }
                if (dueTime.isAfter(previousCheck) && !dueTime.isAfter(now)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intervalDue(LocalDateTime previousCheck, LocalDateTime now) {
        if (schedule.startTime() == null || schedule.intervalSeconds() <= 0L) {
            return false;
        }

        for (LocalDate date = previousCheck.toLocalDate(); !date.isAfter(now.toLocalDate()); date = date.plusDays(1L)) {
            LocalDateTime start = date.atTime(schedule.startTime());
            LocalDateTime dayEnd = date.plusDays(1L).atStartOfDay().minusNanos(1L);
            LocalDateTime effectiveNow = now.isBefore(dayEnd) ? now : dayEnd;
            if (effectiveNow.isBefore(start)) {
                continue;
            }

            long elapsedSeconds = Duration.between(start, effectiveNow).getSeconds();
            long slot = elapsedSeconds / schedule.intervalSeconds();
            LocalDateTime dueTime = start.plusSeconds(slot * schedule.intervalSeconds());
            if (dueTime.isAfter(previousCheck) && !dueTime.isAfter(now)) {
                return true;
            }
        }
        return false;
    }
}
