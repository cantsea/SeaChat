package com.seachat;

record PrivateChatChannel(
        String id,
        boolean enabled,
        boolean toggleable,
        String format,
        String permission,
        String command
) {
}
