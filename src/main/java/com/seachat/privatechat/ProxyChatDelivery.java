package com.seachat.privatechat;

import com.seachat.chat.ChatState;
import com.seachat.network.PrivateChatProtocol.Delivery;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

final class ProxyChatDelivery {
    private ProxyChatDelivery() {}

    static void deliver(Delivery message, Function<UUID, Player> findPlayer, ChatState state) {
        Component normal = null;
        Component disabled = null;
        // The proxy has already checked central permissions and both server lists.
        for (UUID id : new HashSet<>(message.recipients())) {
            Player player = findPlayer.apply(id);
            if (player == null) continue;
            if (state.areColorsDisabled(id) && message.disabled() != null) {
                if (disabled == null) disabled = GsonComponentSerializer.gson().deserialize(message.disabled());
                player.sendMessage(disabled);
            } else {
                if (normal == null) normal = GsonComponentSerializer.gson().deserialize(message.normal());
                player.sendMessage(normal);
            }
        }
    }
}