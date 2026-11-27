# UpgradeKey 1.0.0

A Paper plugin for kill-earned Upgrade Tokens and temporary Demora Legendary Weapons rewards.

## What it does

- A player kill gives the killer +1 Upgrade Token, capped at `max-tokens` (default 5).
- A player killed by another player loses exactly 1 token.
- Environmental deaths, mobs, lava, fall damage, etc. do not remove tokens.
- Tokens are saved in `plugins/UpgradeKey/players.yml`.
- At +5 tokens the plugin grants the player's assigned Demora weapon.
- The granted Demora weapon is tagged as a temporary reward. It cannot be dropped, moved to another inventory by a player or hopper, picked up by a hopper, or swapped to the offhand.
- On every death the tagged weapon is removed from the death drops. It is also cleared after respawn, including servers using `keepInventory`.
- If a player still has 5 tokens after a non-player death, the weapon is granted again after respawn. If a player was killed by another player, they fall to 4 tokens and must earn their way back to 5.
- OPs can use `/upgradekey menu` to choose an online player, then click a Demora weapon to assign it.
- `/upgradekey tokens` opens a display showing earned Upgrade Tokens with the vanilla Trial Key item texture.

## Demora Legendary Weapons 7.0 integration

The uploaded JAR's `plugin.yml` declares the `demorahop` command. Its command implementation accepts:

```text
/demorahop give <weapon_id> <player_name>
```

UpgradeKey uses the configured command template:

```yaml
demora:
  enabled: true
  command: "demorahop give %weapon% %player%"
```

Weapon IDs included from the inspected 7.0 JAR:

`sculk_crossbow`, `midas_sword`, `mjolnir`, `shadow_blade`, `reaper_scythe`, `villager_staff`, `dragon_katana`, `excalibur`, `void_staff`, `magma_club`, `shrink_ray`, `chainsaw_sword`, `wither_sickles`, `awakened_wither_sickles`, `cloud_sword`, `hypnosis_staff`, `emerald_blade`, `golem_hammer`, `toxic_crossbow`, `lich_staff`, `artemis_bow`, `poseidon_trident`, `reinforced_elytra`, `ravager_horn`, `freezing_chakram`, `sculkweavers_lantern`, `war_pick`, `beehive_blaster`, `fugu_cannon`, `harpoon_launcher`, `awakened_dragon_katana`, `rework_elytra`, `elemental_staff`, `admon_stick`.

## Commands

- `/upgradekey menu` — OP weapon assignment menu (online players).
- `/upgradekey menu <player>` — directly open that online player's weapon list.
- `/upgradekey tokens` — show your token progress.
- `/upgradekey give <player> <amount>` — add tokens (capped at maximum).
- `/upgradekey set <player> <amount>` — set a player's token count.
- `/upgradekey reload` — reload `config.yml`.

Permission: `upgradekey.admin` (default: op).

## Build

Requires Java 25+ and Maven. From this folder run:

```bash
mvn package
```

The output JAR will be `target/UpgradeKey-1.0.0.jar`.

The Maven build downloads the Paper API dependency from the PaperMC Maven repository. The build descriptor compiles against Paper API 26.2 stable and sets plugin `api-version: '26.2'`. The plugin uses Bukkit/Paper APIs shared with Purpur, so the same JAR is intended for Paper and Purpur 26.2. Test it on a staging server before using it live.

## Install

1. Build the JAR with Maven.
2. Put `UpgradeKey-1.0.0.jar` and `demoralegendaryweapons-7.0.jar` in the server's `plugins/` folder.
3. Start the server once to generate `plugins/UpgradeKey/config.yml`.
4. Set `default-weapon` or assign players through `/upgradekey menu`.
5. Restart the server.

### Important behavior note

Demora's `/demorahop give` command inserts the weapon into the player's inventory. UpgradeKey checks for a free storage slot first, then tags the newly inserted item. Keep at least one free inventory slot when the reward is due. If the inventory is full, the reward is queued and retried after an inventory interaction or next login.
