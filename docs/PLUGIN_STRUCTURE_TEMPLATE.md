# Minecraft Plugin Structure Template

Use this as a starting layout for Paper or Bukkit plugins that have more than one feature.

```text
src/main/java/com/example/pluginname/
  PluginName.java
  command/
    PluginCommand.java
    PluginPermissions.java
    SubCommand.java
    CommandContext.java
    CommandUtils.java
    subcommand/
      ReloadSubCommand.java
      FeatureSubCommand.java
  listener/
    PlayerListener.java
    CommandVisibilityListener.java
  config/
    PluginSettings.java
    MessageService.java
  feature/
    featureone/
      FeatureOneManager.java
      FeatureOneState.java
      FeatureOneModel.java
    featuretwo/
      FeatureTwoManager.java
      FeatureTwoModel.java
  model/
    SharedModel.java
  service/
    SharedService.java
  util/
    TextUtils.java
src/main/resources/
  plugin.yml
  config.yml
  lang.yml
```

## Package Roles

`PluginName.java`

Owns plugin startup, shutdown, dependency wiring, command registration, listener registration, and reload coordination.

`command`

Owns command dispatch only. The top-level command class should parse the first argument and delegate to subcommands. Each subcommand owns its permission checks, usage text, execution, and tab completion.

`listener`

Owns Bukkit or Paper event classes. Keep event classes small. If an event needs real feature logic, call a manager instead of putting the logic directly in the listener.

`config`

Owns loading config values, lang messages, placeholder rendering, MiniMessage rendering, and reloadable settings.

`feature/<feature-name>`

Owns one plugin feature at a time. Put the manager, state, models, scheduled tasks, and feature-specific helpers together.

`model`

Use this only for shared data objects that more than one feature package needs.

`service`

Use this for shared behavior that is not tied to one feature, such as storage, economy hooks, permissions integration, or placeholder integration.

`util`

Use this sparingly for small pure helpers. If a utility starts depending on Bukkit state or plugin services, it probably belongs in `service` or a feature package.

## Rules Of Thumb

Keep `PluginName.java` boring. It should wire objects together and call lifecycle methods.

Keep commands thin. Commands should validate input, check permissions, and delegate.

Keep listeners thin. Listeners should translate events into feature calls.

Keep managers feature-focused. A manager should own one feature, not all plugin behavior.

Keep config access centralized. Do not scatter raw config path reads throughout listeners and commands.

Keep runtime state explicit. If data expires, make the expiry owner obvious and shut it down in `onDisable`.

Avoid nested classes unless they are tiny and private to one file. If the class represents plugin behavior, storage, commands, or models, give it its own file.

## Example Feature Shape

```text
feature/poll/
  PollManager.java
  ActivePoll.java
  Vote.java
```

`PollManager` creates, stops, and finishes polls.

`ActivePoll` stores one active poll and its votes.

`Vote` parses and represents valid responses.

## Example Command Shape

```text
command/
  ChatCommand.java
  ChatSubCommand.java
  CommandContext.java
  ChatPermissions.java
  subcommand/
    ToggleSubCommand.java
    ReloadSubCommand.java
```

`ChatCommand` dispatches.

`ChatSubCommand` defines the command contract.

`CommandContext` passes shared plugin services to subcommands.

`ChatPermissions` keeps permission nodes in one place.
