package com.seachat.announcement;

import java.time.LocalTime;

record ScheduledTime(LocalTime time, boolean hasSeconds) {
}
