package me.sworroo;

import me.sworroo.commands.BuildCommand;
import me.sworroo.runpod.RunPodModelGenerator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MikoBuild extends JavaPlugin implements Listener {

    private RunPodModelGenerator basicRunpodModelGenerator;
    private RunPodModelGenerator extendedRunpodModelGenerator;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Проверка наличия WorldEdit
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit не найден! Плагин требует WorldEdit для работы.");
            getLogger().severe("Отключение MikoBuild...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Сохраняем конфигурацию по умолчанию
        saveDefaultConfig();
        config = getConfig();

        // Настройка API обработчика
//        apiHandler = new ApiHandler(this, apiUrl);
        this.basicRunpodModelGenerator = new RunPodModelGenerator(
                config.getString("api.basic.key", "key"),
                config.getString("api.basic.endpointId", "endpoint"),
                this.getDataFolder());
        this.extendedRunpodModelGenerator = new RunPodModelGenerator(
                config.getString("api.extended.key", "key"),
                config.getString("api.extended.endpointId", "endpoint"),
                this.getDataFolder());
        // Регистрация команды
        getCommand("miko").setExecutor(new BuildCommand(this, basicRunpodModelGenerator, extendedRunpodModelGenerator));

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("MikoBuild активирован!");
    }

    @EventHandler
    private void onBlockFromTo(BlockFromToEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockGrow(BlockGrowEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockForm(BlockFormEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockFade(BlockFadeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onEntityChangeBlock(EntityChangeBlockEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onLeavesDecay(LeavesDecayEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockIgniteEvent(BlockIgniteEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockBurn(BlockBurnEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onHangingBreak(HangingBreakEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onBlockPhysics(BlockPhysicsEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onDisable() {
        getLogger().info("MikoBuild деактивирован!");
    }
}
