package com.seachat.poll;

import java.util.Locale;

enum Vote {
    YES("yes"),
    NO("no");

    final String messageValue;

    Vote(String messageValue) {
        this.messageValue = messageValue;
    }

    static Vote from(String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "yes" -> YES;
            case "no" -> NO;
            default -> null;
        };
    }
}
