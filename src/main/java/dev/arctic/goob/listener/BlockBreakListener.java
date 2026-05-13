package dev.arctic.goob.listener;

import dev.arctic.goob.Goob;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class BlockBreakListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getEnchantmentLevel(Enchantment.SILK_TOUCH) < 1) return;

        var entry = Goob.get().getMainConfig().getDropFor(event.getBlock().getType());
        if (entry == null) return;

        var drop = new ItemStack(entry.drop(), entry.qty());

        if (entry.model() != null) {
            var meta = drop.getItemMeta();
            if (meta != null) {
                meta.setItemModel(entry.model());
                drop.setItemMeta(meta);
            }
        }

        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
    }
}
