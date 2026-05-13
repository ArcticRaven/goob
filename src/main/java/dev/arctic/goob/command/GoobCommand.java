package dev.arctic.goob.command;

import dev.arctic.goob.Goob;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GoobCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "goob.admin";

    // ── Execution ────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("[Goob] You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) return sendUsage(sender);

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "silk"   -> handleSilk(sender, args);
            default       -> sendUsage(sender);
        };
    }

    private boolean handleReload(CommandSender sender) {
        Goob.get().getMainConfig().reload();
        sender.sendMessage("[Goob] Config reloaded — " +
                Goob.get().getMainConfig().getExtraSilkDrops().size() + " extra-silk entries loaded.");
        return true;
    }

    private boolean handleSilk(CommandSender sender, String[] args) {
        if (args.length < 2) return sendSilkUsage(sender);

        return switch (args[1].toLowerCase()) {
            case "add"    -> handleSilkAdd(sender, args);
            case "remove" -> handleSilkRemove(sender, args);
            default       -> sendSilkUsage(sender);
        };
    }

    // /goob silk add <BASE> <DROP> <QTY> [MODEL]
    private boolean handleSilkAdd(CommandSender sender, String[] args) {
        if (args.length < 5) return sendSilkUsage(sender);

        var block = Material.matchMaterial(args[2]);
        if (block == null) {
            sender.sendMessage("[Goob] Unknown block material: " + args[2]);
            return true;
        }

        var drop = Material.matchMaterial(args[3]);
        if (drop == null) {
            sender.sendMessage("[Goob] Unknown drop material: " + args[3]);
            return true;
        }

        int qty;
        try {
            qty = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("[Goob] Invalid quantity: " + args[4]);
            return true;
        }
        if (qty < 1) {
            sender.sendMessage("[Goob] Quantity must be at least 1.");
            return true;
        }

        NamespacedKey model = null;
        if (args.length >= 6) {
            model = NamespacedKey.fromString(args[5]);
            if (model == null) {
                sender.sendMessage("[Goob] Invalid model key '" + args[5] + "' — expected namespace:key format.");
                return true;
            }
        }

        Goob.get().getMainConfig().addSilkEntry(block, drop, qty, model);
        sender.sendMessage("[Goob] Added silk entry: " + block.name() + " → " + drop.name()
                + " x" + qty + (model != null ? " [" + model + "]" : "") + " (saved to config).");
        return true;
    }

    // /goob silk remove <BASE>
    private boolean handleSilkRemove(CommandSender sender, String[] args) {
        if (args.length < 3) return sendSilkUsage(sender);

        var block = Material.matchMaterial(args[2]);
        if (block == null) {
            sender.sendMessage("[Goob] Unknown block material: " + args[2]);
            return true;
        }

        if (Goob.get().getMainConfig().removeSilkEntry(block)) {
            sender.sendMessage("[Goob] Removed silk entry for " + block.name() + " (saved to config).");
        } else {
            sender.sendMessage("[Goob] No silk entry found for " + block.name() + ".");
        }
        return true;
    }

    // ── Tab completion ───────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();

        return switch (args.length) {
            case 1 -> filter(args[0], "reload", "silk");
            case 2 -> args[0].equalsIgnoreCase("silk") ? filter(args[1], "add", "remove") : List.of();
            case 3 -> switch (args[1].toLowerCase()) {
                case "add"    -> filterMaterials(args[2]);
                case "remove" -> filterConfiguredBlocks(args[2]);
                default -> List.of();
            };
            case 4 -> args[1].equalsIgnoreCase("add") ? filterMaterials(args[3]) : List.of();
            case 5 -> args[1].equalsIgnoreCase("add") ? filter(args[4], "1", "2", "4", "8") : List.of();
            case 6 -> args[1].equalsIgnoreCase("add") ? filter(args[5], "minecraft:") : List.of();
            default -> List.of();
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static List<String> filter(String input, String... options) {
        var lower = input.toLowerCase();
        return Arrays.stream(options).filter(o -> o.toLowerCase().startsWith(lower)).toList();
    }

    private static List<String> filterMaterials(String input) {
        var lower = input.toLowerCase();
        return Stream.of(Material.values())
                .filter(m -> !m.isLegacy())
                .map(Material::name)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .limit(50)
                .toList();
    }

    private static List<String> filterConfiguredBlocks(String input) {
        var lower = input.toLowerCase();
        return Goob.get().getMainConfig().getExtraSilkDrops().keySet().stream()
                .map(Material::name)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .sorted()
                .toList();
    }

    private boolean sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /goob <reload|silk>");
        return true;
    }

    private boolean sendSilkUsage(CommandSender sender) {
        sender.sendMessage("Usage: /goob silk add <BASE> <DROP> <QTY> [MODEL]");
        sender.sendMessage("       /goob silk remove <BASE>");
        return true;
    }
}
