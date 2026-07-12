package com.seachat.privatechat;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

final class PrivateChatCommand extends Command {
    private final PrivateChatChannel channel;
    private final PrivateChatManager manager;

    PrivateChatCommand(PrivateChatChannel channel, PrivateChatManager manager) {
        super(channel.command());
        this.channel = channel;
        this.manager = manager;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return manager.execute(channel, sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }

    PrivateChatChannel channel() {
        return channel;
    }
}
