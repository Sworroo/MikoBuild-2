package me.sworroo.commands;

import com.sk89q.worldedit.regions.Region;
import me.sworroo.api.ApiHandler;
import me.sworroo.builder.ModelBuilder;
import me.sworroo.utils.RegionSelector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class BuildCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ApiHandler apiHandler;
    private final RegionSelector regionSelector;

    public BuildCommand(JavaPlugin plugin, ApiHandler apiHandler) {
        this.plugin = plugin;
        this.apiHandler = apiHandler;
        this.regionSelector = new RegionSelector(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда может использоваться только игроками!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /miko <описание>");
            return false;
        }

        Player player = (Player) sender;
        StringBuilder promptBuilder = new StringBuilder();

        // Собираем описание из аргументов
        for (int i = 0; i < args.length; i++) {
            promptBuilder.append(args[i]).append(" ");
        }

        String prompt = promptBuilder.toString().trim();

        // Пытаемся получить выделение WorldEdit
        try {
            Region region = regionSelector.getPlayerSelection(player);
            Vector dimensions = regionSelector.getRegionDimensions(region);

            player.sendMessage("§aГенерация постройки: §e" + prompt + "§a в выбранном регионе");

            // Асинхронно запрашиваем генерацию модели
            apiHandler.generateModel(prompt, Math.max(1, (int)Math.max(dimensions.getX(), Math.max(dimensions.getY(), dimensions.getZ()))))
                    .thenAccept(modelFile -> {
                        // Запуск задачи в основном потоке
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§aМодель сгенерирована, начинаем строительство...");

                            // Строим модель в мире Minecraft в выбранном регионе
                            new ModelBuilder(plugin).buildFromModel(
                                    modelFile,
                                    regionSelector.getMinLocation(region, player.getWorld()),
                                    dimensions,
                                    progress -> {
                                        if (progress % 0.1 < 0.01) { // Отправляем сообщение только каждые 10%
                                            player.sendMessage("§aПрогресс строительства: §e" + (int)(progress * 100) + "%");
                                        }
                                    }
                            ).thenAccept(result -> {
                                // Запуск задачи в основном потоке
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage("§aСтроительство завершено!");
                                });
                            });
                        });
                    })
                    .exceptionally(ex -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§cОшибка при генерации модели: " + ex.getMessage());
                            player.sendMessage("§cГенерация моделей на CPU может занимать 3-5 минут. Пожалуйста, попробуйте еще раз и наберитесь терпения.");
                        });
                        return null;
                    });
        } catch (Exception e) {
            player.sendMessage("§cОшибка: " + e.getMessage());
            player.sendMessage("§cУбедитесь, что вы выделили регион с помощью WorldEdit (команды //wand, //pos1, //pos2)");
            return true;
        }

        return true;
    }
}