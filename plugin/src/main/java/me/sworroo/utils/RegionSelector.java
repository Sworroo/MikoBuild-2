package me.sworroo.utils;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class RegionSelector {

    private final JavaPlugin plugin;

    public RegionSelector(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получает текущий выделенный регион игрока в WorldEdit
     */
    public Region getPlayerSelection(Player player) throws Exception {
        WorldEditPlugin worldEdit = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null) {
            throw new IllegalStateException("WorldEdit не найден на сервере!");
        }

        com.sk89q.worldedit.bukkit.selections.Selection selection = worldEdit.getSelection(player);
        if (selection == null) {
            throw new IllegalStateException("Выберите регион с помощью WorldEdit сначала!");
        }

        return selection.getRegionSelector().getRegion();
    }

    /**
     * Получает размеры региона
     */
    public Vector getRegionDimensions(Region region) {
        return new Vector(
                region.getWidth(),
                region.getHeight(),
                region.getLength()
        );
    }

    /**
     * Получает минимальную точку региона в виде Location
     */
    public Location getMinLocation(Region region, org.bukkit.World world) {
        com.sk89q.worldedit.Vector min = region.getMinimumPoint();
        return new Location(world, min.getX(), min.getY(), min.getZ());
    }

    /**
     * Проверяет, поместится ли модель указанного размера в регион
     */
    public boolean canFitInRegion(Region region, int modelSize) {
        Vector dimensions = getRegionDimensions(region);
        return dimensions.getX() >= modelSize &&
                dimensions.getY() >= modelSize &&
                dimensions.getZ() >= modelSize;
    }
}