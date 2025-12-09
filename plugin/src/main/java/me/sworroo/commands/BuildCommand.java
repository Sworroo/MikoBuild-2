package me.sworroo.commands;

import com.sk89q.worldedit.regions.Region;
import me.sworroo.builder.ModelBuilder;
import me.sworroo.runpod.RunPodModelGenerator;
import me.sworroo.utils.RegionSelector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuildCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final RunPodModelGenerator modelGenerator;
    private final RegionSelector regionSelector;

    // Для отслеживания активных генераций (опционально)
    private final Map<UUID, String> activeGenerations = new HashMap<>();

    public BuildCommand(JavaPlugin plugin, RunPodModelGenerator modelGenerator) {
        this.plugin = plugin;
        this.modelGenerator = modelGenerator;
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
        UUID playerId = player.getUniqueId();

        // Проверяем, нет ли уже активной генерации у игрока
        if (activeGenerations.containsKey(playerId)) {
            player.sendMessage("§cУ вас уже есть активная генерация. Пожалуйста, дождитесь её завершения.");
            return true;
        }

        // Собираем описание из аргументов
        StringBuilder promptBuilder = new StringBuilder();
        for (String arg : args) {
            promptBuilder.append(arg).append(" ");
        }
        String prompt = promptBuilder.toString().trim();
        boolean nonTextured;
        if(prompt.endsWith("-c")){
            nonTextured = true;
            prompt = prompt.substring(0, prompt.length() - 2);
        } else {
            nonTextured = false;
        }

        // Пытаемся получить выделение WorldEdit
        try {
            Region region = regionSelector.getPlayerSelection(player);
            Vector dimensions = regionSelector.getRegionDimensions(region);

            player.sendMessage("§a⏳ Генерация постройки: §e" + prompt);
            player.sendMessage("§7Размер региона: §f" + (int)dimensions.getX() + "x" + (int)dimensions.getY() + "x" + (int)dimensions.getZ());
            player.sendMessage("§7Пожалуйста, подождите... Это может занять 1-3 минуты.");

            // Отмечаем, что у игрока есть активная генерация
            activeGenerations.put(playerId, prompt);

            // Рассчитываем параметры генерации на основе размера региона
            double maxDimension = Math.max(dimensions.getX(), Math.max(dimensions.getY(), dimensions.getZ()));

            // Настраиваем guidance_scale и num_steps в зависимости от сложности
            double guidanceScale = 15.0;
            int numSteps = 64;

            // Для больших построек можно увеличить детализацию
            if (maxDimension > 50) {
                numSteps = 96;
            }

            // Асинхронно запрашиваем генерацию модели
            String finalPrompt = prompt;
            modelGenerator.generateModel(prompt, guidanceScale, numSteps, false)
                    .thenAccept(modelResult -> {
                        // Запуск задачи в основном потоке Bukkit
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§a✅ Модель сгенерирована! Начинаем строительство...");

                            // Получаем OBJ файл из результата
                            if (modelResult.getObjFile() == null || !modelResult.getObjFile().exists()) {
                                player.sendMessage("§cОшибка: OBJ файл не найден в результате генерации");
                                activeGenerations.remove(playerId);
                                return;
                            }

                            // Строим модель в мире Minecraft
                            new ModelBuilder(plugin).buildFromModel(
                                    modelResult.getObjFile(),
                                    regionSelector.getMinLocation(region, player.getWorld()),
                                    dimensions,
                                    progress -> {
                                        // Отправляем сообщение каждые 10%
                                        int progressPercent = (int)(progress * 100);
                                        if (progressPercent % 10 == 0 && progress > 0) {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                player.sendMessage("§a🔨 Прогресс строительства: §e" + progressPercent + "%");
                                            });
                                        }
                                    },
                                    nonTextured
                            ).thenAccept(result -> {
                                // Завершение в основном потоке
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage("§a✅ Строительство завершено!");
                                    player.sendMessage("§7Постройка: §f" + finalPrompt);

                                    // Убираем из активных генераций
                                    activeGenerations.remove(playerId);

                                    // Опционально: очищаем временные файлы
                                    // modelResult.cleanup();
                                });
                            }).exceptionally(buildError -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    player.sendMessage("§cОшибка при строительстве: " + buildError.getMessage());
                                    activeGenerations.remove(playerId);
                                });
                                return null;
                            });
                        });
                    })
                    .exceptionally(ex -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Убираем из активных генераций
                            activeGenerations.remove(playerId);

                            // Форматируем сообщение об ошибке
                            String errorMessage = ex.getMessage();
                            if (ex.getCause() != null) {
                                errorMessage = ex.getCause().getMessage();
                            }

                            player.sendMessage("§c❌ Ошибка при генерации модели:");
                            player.sendMessage("§c   " + errorMessage);

                            if (errorMessage != null && errorMessage.contains("timed out")) {
                                player.sendMessage("§eСервер генерации перегружен. Попробуйте позже.");
                            } else {
                                player.sendMessage("§7Попробуйте изменить описание или повторить попытку позже.");
                            }
                        });
                        return null;
                    });

        } catch (Exception e) {
            player.sendMessage("§c❌ Ошибка: " + e.getMessage());
            player.sendMessage("§7Убедитесь, что вы выделили регион с помощью WorldEdit:");
            player.sendMessage("§7  1. §f//wand §7- получить инструмент выделения");
            player.sendMessage("§7  2. ЛКМ и ПКМ по блокам для выделения региона");
            player.sendMessage("§7  или §f//pos1 §7и §f//pos2");
            return true;
        }

        return true;
    }

    /**
     * Проверяет, есть ли у игрока активная генерация
     */
    public boolean hasActiveGeneration(UUID playerId) {
        return activeGenerations.containsKey(playerId);
    }

    /**
     * Получает промпт активной генерации игрока
     */
    public String getActiveGenerationPrompt(UUID playerId) {
        return activeGenerations.get(playerId);
    }
}
