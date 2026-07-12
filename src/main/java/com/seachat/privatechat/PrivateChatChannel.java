package com.seachat.privatechat;

public record PrivateChatChannel(
        String id,
        boolean enabled,
        boolean toggleable,
        String format,
        String permission,
        String command
) {
}
