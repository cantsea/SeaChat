package com.seachat.announcement;

import com.seachat.SeaChat;
import com.seachat.config.ChatSettings;
import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class AnnouncementManager {
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("H:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private final SeaChat plugin;
    private final ChatSettings settings;
    private BukkitTask task;
    private List<Announcement> announcements = List.of();
    private LocalDateTime lastCheckTime;

    public AnnouncementManager(SeaChat plugin, ChatSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void reload() {
        shutdown();

        if (!settings.announcementsEnabled()) {
            announcements = List.of();
            return;
        }

        announcements = loadAnnouncements();
        if (announcements.isEmpty()) {
            return;
        }

        if (announcements.stream().noneMatch(Announcement::hasScheduledActivation)) {
            return;
        }

        lastCheckTime = LocalDateTime.now().minusSeconds(1L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        announcements = List.of();
        lastCheckTime = null;
    }

    private List<Announcement> loadAnnouncements() {
        File file = new File(plugin.getDataFolder(), "announcements.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("announcements");
        if (section == null) {
            return List.of();
        }

        List<Announcement> loaded = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection announcementSection = section.getConfigurationSection(id);
            if (announcementSection == null || !announcementSection.getBoolean("enabled", true)) {
                continue;
            }

            List<String> messages = loadMessages(announcementSection);
            if (messages.isEmpty()) {
                plugin.getLogger().warning("Skipping announcement '" + id + "' because it has no messages.");
                continue;
            }

            String trigger = loadTrigger(announcementSection);
            AnnouncementSchedule schedule = loadSchedule(id, announcementSection);
            if (!schedule.hasValidSchedule() && trigger.isBlank()) {
                plugin.getLogger().warning("Skipping announcement '" + id
                        + "' because it has no valid enabled schedule or trigger.");
                continue;
            }

            if (schedule.intervalEnabled() && schedule.timesEnabled()) {
                plugin.getLogger().info("Announcement '" + id
                        + "' has both interval and defined times enabled. Interval schedule will be used.");
            }

            loaded.add(new Announcement(id, messages, schedule, trigger));
        }

        plugin.getLogger().info("Loaded " + loaded.size() + " announcement(s).");
        return List.copyOf(loaded);
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousCheck = lastCheckTime == null ? now.minusSeconds(1L) : lastCheckTime;
        try {
            for (Announcement announcement : announcements) {
                if (announcement.shouldSend(previousCheck, now)) {
                    sendAnnouncement(announcement);
                }
            }
        } finally {
            lastCheckTime = now;
        }
    }

    private void sendAnnouncement(Announcement announcement) {
        sendAnnouncement(announcement, Map.of());
    }

    public boolean trigger(String trigger, Map<String, String> placeholders) {
        boolean triggered = false;
        for (Announcement announcement : announcements) {
            if (!announcement.trigger().equalsIgnoreCase(trigger)) {
                continue;
            }

            sendAnnouncement(announcement, placeholders);
            triggered = true;
        }
        return triggered;
    }

    public boolean test(String id, Map<String, String> placeholders) {
        for (Announcement announcement : announcements) {
            if (!announcement.id().equalsIgnoreCase(id)) {
                continue;
            }

            sendAnnouncement(announcement, placeholders);
            return true;
        }
        return false;
    }

    public List<String> announcementIds() {
        return announcements.stream()
                .map(Announcement::id)
                .toList();
    }

    private void sendAnnouncement(Announcement announcement, Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String message : announcement.messages()) {
                player.sendMessage(settings.announcementMessage(player, message, placeholders));
            }
        }
    }

    private List<String> loadMessages(ConfigurationSection section) {
        List<String> messages = section.getStringList("messages");
        if (!messages.isEmpty()) {
            return messages;
        }

        String message = section.getString("message", "");
        if (message.isBlank()) {
            return List.of();
        }
        return List.of(message);
    }

    private AnnouncementSchedule loadSchedule(String id, ConfigurationSection section) {
        ConfigurationSection definedTimesSection = section.getConfigurationSection("defined-times");
        ConfigurationSection intervalSection = section.getConfigurationSection("interval");

        boolean timesEnabled = definedTimesSection == null
                ? section.isList("times")
                : definedTimesSection.getBoolean("enabled", true);
        List<ScheduledTime> times = loadTimes(id, definedTimesSection == null ? section : definedTimesSection);

        boolean intervalEnabled = intervalSection == null
                ? hasLegacyInterval(section)
                : intervalSection.getBoolean("enabled", true);
        ConfigurationSection intervalSource = intervalSection == null ? section : intervalSection;
        LocalTime startTime = loadTime(id, intervalSource.getString("start", ""), "interval.start");
        long intervalSeconds = loadIntervalSeconds(intervalSource);
        if (intervalEnabled && (intervalSeconds <= 0L || startTime == null)) {
            plugin.getLogger().warning("Announcement '" + id
                    + "' has interval enabled but no valid interval start/time.");
        }

        return new AnnouncementSchedule(timesEnabled, times, intervalEnabled, startTime, intervalSeconds);
    }

    private String loadTrigger(ConfigurationSection section) {
        ConfigurationSection triggerSection = section.getConfigurationSection("trigger");
        if (triggerSection == null) {
            String trigger = section.getString("trigger", "");
            return trigger == null ? "" : trigger.trim().toLowerCase(Locale.ROOT);
        }

        if (!triggerSection.getBoolean("enabled", true)) {
            return "";
        }

        String trigger = triggerSection.getString("name", "");
        return trigger == null ? "" : trigger.trim().toLowerCase(Locale.ROOT);
    }

    private List<ScheduledTime> loadTimes(String id, ConfigurationSection section) {
        List<ScheduledTime> times = new ArrayList<>();
        for (String rawTime : section.getStringList("times")) {
            LocalTime time = loadTime(id, rawTime, "times");
            if (time != null) {
                times.add(new ScheduledTime(time, rawTime.trim().split(":").length >= 3));
            }
        }
        return List.copyOf(times);
    }

    private boolean hasLegacyInterval(ConfigurationSection section) {
        return section.isSet("start")
                || section.isSet("interval")
                || section.isSet("interval-seconds")
                || section.isSet("interval-minutes")
                || section.isSet("interval-hours");
    }

    private LocalTime loadTime(String id, String rawTime, String path) {
        if (rawTime == null || rawTime.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(rawTime.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            plugin.getLogger().warning("Announcement '" + id + "' has invalid " + path
                    + " time '" + rawTime + "'. Use HH:mm or HH:mm:ss.");
            return null;
        }
    }

    private long loadIntervalSeconds(ConfigurationSection section) {
        long seconds = section.getLong("interval-seconds", 0L);
        if (seconds <= 0L) {
            seconds = section.getLong("interval-minutes", 0L) * 60L;
        }
        if (seconds <= 0L) {
            seconds = section.getLong("interval-hours", 0L) * 3600L;
        }
        if (seconds <= 0L) {
            seconds = parseInterval(section.getString("interval", ""));
        }
        return Math.max(0L, seconds);
    }

    private long parseInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return 0L;
        }

        String normalized = interval.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (normalized.endsWith("seconds") || normalized.endsWith("second")) {
            normalized = normalized.replaceFirst("seconds?$", "").trim();
        } else if (normalized.endsWith("minutes") || normalized.endsWith("minute")) {
            normalized = normalized.replaceFirst("minutes?$", "").trim();
            multiplier = 60L;
        } else if (normalized.endsWith("hours") || normalized.endsWith("hour")) {
            normalized = normalized.replaceFirst("hours?$", "").trim();
            multiplier = 3600L;
        } else if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        } else if (normalized.endsWith("m")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            multiplier = 60L;
        } else if (normalized.endsWith("h")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            multiplier = 3600L;
        }

        try {
            return Long.parseLong(normalized) * multiplier;
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

}
