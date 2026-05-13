# Goob

Custom Silk Touch drop behaviour for Paper servers. Configure any block to drop itself or a completely different item, with a custom quantity and optional item model for resource-pack custom blocks.

---

## Requirements

- Paper 26.1.2+
- Java 25+

---

## Installation

1. Drop `goob.jar` into your `plugins/` folder.
2. Start or reload the server — `plugins/Goob/config.yml` is generated automatically.
3. Edit the config, then run `/goob reload` to apply changes without restarting.

---

## Configuration

**`plugins/Goob/config.yml`**

Each entry under `extra-silk` is keyed by a block material name (case-insensitive). Three formats are supported:

```yaml
extra-silk:

  # Shorthand — drops the block itself, qty 1
  STONE:
  DEEPSLATE:

  # Subsection — custom quantity, still drops itself
  COAL_ORE:
    qty: 3

  # Full override — drop a different item
  BUDDING_AMETHYST:
    drop: AMETHYST_BLOCK
    qty: 2

  # With an optional item model for resource-pack custom blocks
  ANCIENT_DEBRIS:
    drop: NETHERITE_SCRAP
    qty: 1
    model: mypack:ancient_debris_raw
```

### Fields

| Field   | Required | Description |
|---------|----------|-------------|
| `drop`  | No | Material to drop. Defaults to the block itself. |
| `qty`   | No | Number of items to drop. Must be ≥ 1. Defaults to 1. |
| `model` | No | Item model key (`namespace:key`) applied to the drop's ItemMeta. Useful for resource-pack custom blocks. |

### Validation

Invalid entries are skipped with a console warning — the plugin never disables itself over a bad config value. Specifically:

- Unknown block material key → warning, entry skipped
- Unknown drop material → warning, entry skipped
- `qty` less than 1 → warning, entry skipped
- Malformed `model` key → warning, model ignored (entry still loads)

Only a structurally broken YAML file will cause the plugin to disable on startup.

---

## Commands

All commands require the `goob.admin` permission (default: op). Tab completion is supported throughout.

### `/goob reload`
Re-reads `config.yml` from disk and rebinds all entries. Use this after manually editing the file.

### `/goob silk add <BASE> <DROP> <QTY> [MODEL]`
Adds or replaces a silk-touch entry at runtime and saves it to `config.yml` immediately.

```
/goob silk add STONE STONE 1
/goob silk add COAL_ORE COAL 3
/goob silk add BUDDING_AMETHYST AMETHYST_BLOCK 2
/goob silk add BUDDING_AMETHYST AMETHYST_BLOCK 2 mypack:custom_amethyst
```

| Argument | Description |
|----------|-------------|
| `BASE`   | Block material to intercept. |
| `DROP`   | Item to drop. Pass the same material as `BASE` to drop the block itself. |
| `QTY`    | How many items to drop. Must be 1 or greater. |
| `MODEL`  | *(Optional)* Item model key in `namespace:key` format. |

### `/goob silk remove <BASE>`
Removes a configured entry and saves the change to `config.yml`. Tab completion only suggests currently configured blocks.

```
/goob silk remove BUDDING_AMETHYST
```

---

## Permissions

| Permission   | Default | Description |
|--------------|---------|-------------|
| `goob.admin` | op      | Access to all `/goob` commands. |

---

## Behaviour Notes

- Only triggers when the player's main-hand item has Silk Touch. All other tools are unaffected.
- When an entry matches, vanilla item drops are suppressed and replaced with the configured drop.
- XP drop behaviour is unchanged — Silk Touch already suppresses XP from ore blocks in vanilla.
- The listener runs at `HIGH` priority with `ignoreCancelled = true`, so it plays nicely with plugins that cancel block breaks.
- Runtime changes via `/goob silk add` and `/goob silk remove` take effect immediately and persist to `config.yml` — no restart or reload needed.
