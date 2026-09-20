package com.seachat.config;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatSettingsTest {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<String, String> VALUES = new HashMap<>();
    private static final Map<String, Integer> CALLS = new HashMap<>();
    private static UnaryOperator<String> resolver;
    private Method method;

    @Before
    public void setUp() throws Exception {
        VALUES.clear();
        CALLS.clear();
        resolver = placeholder -> VALUES.getOrDefault(placeholder, placeholder);
        method = ChatSettingsTest.class.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
    }

    public static String setPlaceholders(OfflinePlayer player, String placeholder) {
        CALLS.merge(placeholder, 1, Integer::sum);
        return resolver.apply(placeholder);
    }

    @Test
    public void expandsLuckPermsPrefixThroughMultipleLevels() throws Exception {
        VALUES.put("%luckperms_prefix%", "[%rank_label%]");
        VALUES.put("%rank_label%", "%rank_name%");
        VALUES.put("%rank_name%", "Admin");

        assertEquals("[Admin] Alex", plain(expand("%luckperms_prefix% Alex")));
    }

    @Test
    public void convertsLegacyFormattingAfterNestedExpansion() throws Exception {
        VALUES.put("%luckperms_prefix%", "&a[%rank_name%]");
        VALUES.put("%rank_name%", "&bAdmin");
        VALUES.put("%expected%", "&a[&bAdmin]");

        assertEquals(expand("%expected%"), expand("%luckperms_prefix%"));
    }

    @Test
    public void preservesMiniMessageFormattingInNestedValues() throws Exception {
        VALUES.put("%luckperms_prefix%", "%rank_name%");
        VALUES.put("%rank_name%", "<gradient:red:blue>Admin</gradient>");

        assertEquals("<bold><gradient:red:blue>Admin</gradient></bold>",
                expand("<bold>%luckperms_prefix%</bold>"));
    }

    @Test
    public void expandsRepeatedAndSiblingPlaceholdersIndependently() throws Exception {
        VALUES.put("%luckperms_prefix%", "%rank_name%/%rank_name% %rank_icon%");
        VALUES.put("%rank_name%", "Admin");
        VALUES.put("%rank_icon%", "*");

        assertEquals("Admin/Admin * Admin/Admin *", plain(expand("%luckperms_prefix% %luckperms_prefix%")));
    }

    @Test
    public void preservesUnknownPlaceholdersWhileExpandingKnownSiblings() throws Exception {
        VALUES.put("%luckperms_prefix%", "%unknown_value% %rank_name%");
        VALUES.put("%rank_name%", "Admin");

        assertEquals("%unknown_value% Admin", plain(expand("%luckperms_prefix%")));
        assertEquals(Integer.valueOf(1), CALLS.get("%unknown_value%"));
    }

    @Test
    public void stopsDirectSelfReferences() throws Exception {
        VALUES.put("%loop_value%", "x%loop_value%");

        assertEquals("x%loop_value%", plain(expand("%loop_value%")));
        assertEquals(Integer.valueOf(1), CALLS.get("%loop_value%"));
    }

    @Test
    public void stopsIndirectCyclesAndStillExpandsSiblings() throws Exception {
        VALUES.put("%first_value%", "A%second_value%");
        VALUES.put("%second_value%", "B%first_value%");
        VALUES.put("%rank_name%", "Admin");

        assertEquals("AB%first_value% Admin", plain(expand("%first_value% %rank_name%")));
        assertEquals(Integer.valueOf(1), CALLS.get("%first_value%"));
        assertEquals(Integer.valueOf(1), CALLS.get("%second_value%"));
    }

    @Test
    public void limitsDepthEvenWhenEachPlaceholderIsDifferent() throws Exception {
        resolver = placeholder -> "%depth_" + (Integer.parseInt(placeholder.substring(7, placeholder.length() - 1)) + 1) + "%";

        assertEquals("%depth_10%", plain(expand("%depth_0%")));
        assertEquals(10, CALLS.size());
    }

    @Test
    public void preservesReplacementCharactersAndEmptyValues() throws Exception {
        VALUES.put("%luckperms_prefix%", "%rank_name% %empty_value%");
        VALUES.put("%rank_name%", "$5\\rank");
        VALUES.put("%empty_value%", "");

        assertEquals("$5\\rank ", plain(expand("%luckperms_prefix%")));
    }

    @Test
    public void leavesPlayerMessageComponentsUnexpanded() throws Exception {
        VALUES.put("%luckperms_prefix%", "%rank_name%");
        VALUES.put("%rank_name%", "Admin");
        Component rendered = MINI_MESSAGE.deserialize(expand("%luckperms_prefix% <message>"),
                Placeholder.component("message", Component.text("%rank_name% <red>hello")));

        assertEquals("Admin %rank_name% <red>hello", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    @Test
    public void loadsNamedAndHexDisabledColors() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.disabled-color", "  DARK_GRAY  ");
        assertEquals(NamedTextColor.DARK_GRAY, ChatSettings.from(config, new YamlConfiguration()).disabledChatColor());

        config.set("chat-format.disabled-color", "#AaBbCc");
        assertEquals(TextColor.color(0xAABBCC), ChatSettings.from(config, new YamlConfiguration()).disabledChatColor());
    }

    @Test
    public void defaultsMissingOrInvalidDisabledColorsToWhite() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(NamedTextColor.WHITE, ChatSettings.from(config, new YamlConfiguration()).disabledChatColor());
        for (String invalid : new String[]{"", "rainbow", "#12345", "#GGHHII", "<red>"}) {
            config.set("chat-format.disabled-color", invalid);
            assertEquals(NamedTextColor.WHITE, ChatSettings.from(config, new YamlConfiguration()).disabledChatColor());
        }
    }

    private String expand(String template) throws Exception {
        return ChatSettings.expandPlaceholders(null, template, method);
    }

    private String plain(String template) {
        return PlainTextComponentSerializer.plainText().serialize(MINI_MESSAGE.deserialize(template));
    }
}
