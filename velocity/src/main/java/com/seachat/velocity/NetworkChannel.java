package com.seachat.velocity;

import java.util.Locale;
import java.util.Set;

record NetworkChannel(String id, String command, String permission, String format, boolean toggleable,
                      Set<String> whitelist, Set<String> blacklist) {
    NetworkChannel {
        whitelist = Set.copyOf(whitelist);
        blacklist = Set.copyOf(blacklist);
    }

    boolean allows(String server) {
        String name = server.toLowerCase(Locale.ROOT);
        return !blacklist.contains(name) && (whitelist.isEmpty() || whitelist.contains(name));
    }
}
