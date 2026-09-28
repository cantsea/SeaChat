# Central private channels (Velocity)

Install the updated `target/SeaChat-1.0-SNAPSHOT.jar` on each participating Paper
server and `velocity/target/SeaChat-Velocity-1.0-SNAPSHOT.jar` in Velocity's
`plugins` directory. Restart the servers and proxy.

Configure network channels **only** in `plugins/seachat/config.yml` on Velocity.
No matching backend channel, command, permission setting, or `proxy-wide` flag is needed.

```yaml
private-chats:
  staff:
    enabled: true
    toggleable: true
    format: "<yellow><b>[STAFF]</b> {sender} » <message>"
    permission: "seachat.chat.staff"
    command: "sc"
    servers:
      whitelist: []
      blacklist: ["limbo"]
```

- Use the server names from Velocity's configuration, not world names.
- Empty or omitted whitelist allows every server. A nonempty whitelist allows only its listed servers.
- Blacklist always wins. Restrictions apply to both sending and receiving.
- Permissions are checked on **Velocity** for senders and recipients, including after rendering.
  Install/configure your proxy permission provider, such as LuckPerms on Velocity, and grant
  the configured permission there. Backend-only permission grants are not sufficient.
- Server-only channels remain under `private-chats` in each backend's SeaChat config.
  Use distinct command names for local and network channels: a network command takes
  priority over a backend command with the same label, even when access is denied.
- A conflict with another **proxy** plugin's command rejects the reload and is logged.

## Commands

- `/sc message`: send to the network staff channel.
- `/sc`: toggle that channel when `toggleable: true`. Wait for the enable confirmation.
- `/seachatproxy leave`: exit network chat mode, including after a channel is removed.
- `/seachatproxy reload`: reload the central config; requires `seachat.proxy.reload`.
  The proxy console can also run this command.

Changing servers or disconnecting ends the network toggle. Enabling a network toggle
clears a previous local toggle; toggling a local channel exits network mode. Editing
backend config with `/chat reload` does not change the central channel definitions.

Proxy status messages can be customized in the proxy config's `messages` section.
The backend's bridge failure message remains in its `lang.yml`.

## Formatting and delivery

Velocity sends the central format and message to the sender's backend for rendering.
That backend resolves sender placeholders (including nested PlaceholderAPI values) and
the sender-dependent disabled message color. Player text stays literal rather than
being interpreted as MiniMessage. The proxy selects authorized recipients on allowed
servers; their backends apply their existing `/chat toggle colors` preference only
to the message component. Clicks, hovers, prefixes, and suffixes are preserved.

The source renders a normal and disabled-color variant once each. Messages are sent
in bounded recipient batches and reused for viewers with the same preference.
No channel definitions or permissions need synchronization between backend configs.

The backend intercepts toggled chat before public broadcasting; the proxy does not
rewrite or cancel signed chat packets. Toggle activation is confirmed only after the
backend acknowledges capture. If forwarding fails while capture is active, the text
stays private and the player receives a failure notice. Network channels do not fall
back to local/public delivery. Run `/seachatproxy leave` to explicitly exit capture.
Use full restarts when replacing plugin JARs; runtime plugin unloading is unsupported.

This supports live chat on **one Velocity proxy**, without Redis or a database.
There is no offline queue or message history. The Paper bridge must be installed on
every participating server. Requests time out after about five seconds if the rendering
backend is missing or unresponsive. Server switches, lost connections, very large
messages, or configuration changes can prevent an in-flight message from arriving;
there are no automatic retries. Proxy console messages use an allowed occupied
backend to render, so at least one such server must have a connected player.

Backends must accept connections only from your trusted proxy, with Velocity forwarding
properly configured. The companion consumes its plugin channel in both directions,
rejects client-originating traffic, and only accepts render replies corresponding to
a pending request on the original server connection.

## Building

Use Java 21 or newer:

```text
mvn package
mvn -f velocity/pom.xml package
```

The proxy module targets the Velocity 3.4 API and bundles its relocated YAML parser.
It shares only platform-independent protocol sources with the backend and does not
bundle Bukkit/Paper classes.