package me.sworroo.builder;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
public class ModelBuilder {

    private final JavaPlugin plugin;

    // Расширенная карта цветов для лучшего сопоставления материалов
    private static final Map<Color, BlockData> COLOR_TO_MATERIAL_MAP = initColorMap();

    private static Map<Color, BlockData> initColorMap() {
        Map<Color, BlockData> map = new HashMap<>();
        // RGB -> Material (расширенная карта цветов)
        map.put(new Color(125, 125, 125), new BlockData(Material.STONE)); // Серый цвет -> Камень
        map.put(new Color(134, 96, 67), new BlockData(Material.DIRT)); // Коричневый -> Земля
        map.put(new Color(145, 189, 89), new BlockData(Material.STAINED_CLAY, (byte)5)); // Зеленый -> Трава
        map.put(new Color(138, 102, 72), new BlockData(Material.WOOD)); // Темно-коричневый -> Дерево
        map.put(new Color(180, 144, 90), new BlockData(Material.SANDSTONE)); // Светло-коричневый -> Песчаник
        map.put(new Color(174, 174, 174), new BlockData(Material.COBBLESTONE)); // Светло-серый -> Булыжник
        map.put(new Color(143, 143, 143), new BlockData(Material.IRON_BLOCK)); // Темно-серый -> Железный блок
        map.put(new Color(255, 254, 255), new BlockData(Material.QUARTZ_BLOCK)); // Белый -> Кварц
        map.put(new Color(20, 20, 20), new BlockData(Material.OBSIDIAN)); // Черный -> Обсидиан
        map.put(new Color(216, 45, 5), new BlockData(Material.REDSTONE_BLOCK)); // Красный -> Редстоун блок
        map.put(new Color(38, 139, 217), new BlockData(Material.DIAMOND_BLOCK)); // Синий -> Алмазный блок
        map.put(new Color(255, 215, 57), new BlockData(Material.GOLD_BLOCK)); // Желтый -> Золотой блок
        map.put(new Color(126, 158, 88), new BlockData(Material.LEAVES)); // Оливковый -> Листва
        map.put(new Color(67, 165, 238), new BlockData(Material.LAPIS_BLOCK)); // Голубой -> Лазуритовый блок
        map.put(new Color(179, 76, 76), new BlockData(Material.BRICK)); // Кирпичный цвет -> Кирпич
        map.put(new Color(103, 86, 68), new BlockData(Material.WOOD_STEP)); // Темное дерево -> Деревянная ступенька
        map.put(new Color(181, 140, 64), new BlockData(Material.WOOL, (byte)4)); // Рыжеватый -> Желтая шерсть
        map.put(new Color(40, 40, 40), new BlockData(Material.COAL_BLOCK)); // Очень темный -> Угольный блок
        map.put(new Color(129, 130, 133), new BlockData(Material.CLAY)); // Светло-серый -> Глина
        map.put(new Color(160, 83, 65), new BlockData(Material.HARD_CLAY)); // Терракотовый -> Обожженная глина
        map.put(new Color(210, 100, 70), new BlockData(Material.STAINED_CLAY, (byte)1)); // Оранжевая глина
        map.put(new Color(95, 169, 24), new BlockData(Material.STAINED_CLAY, (byte)5)); // Зеленая глина
        map.put(new Color(241, 118, 19), new BlockData(Material.PUMPKIN)); // Оранжевый -> Тыква
        map.put(new Color(119, 89, 55), new BlockData(Material.WOOD, (byte)1)); // Еловое дерево
        map.put(new Color(88, 54, 22), new BlockData(Material.WOOD, (byte)3)); // Темное дерево (джунгли)

        // Добавляем разные сорта шерсти для богатой цветовой палитры
        map.put(new Color(234, 236, 236), new BlockData(Material.WOOL, (byte)0)); // Белая шерсть
        map.put(new Color(240, 118, 19), new BlockData(Material.WOOL, (byte)1)); // Оранжевая шерсть
        map.put(new Color(189, 68, 179), new BlockData(Material.WOOL, (byte)2)); // Пурпурная шерсть
        map.put(new Color(58, 175, 217), new BlockData(Material.WOOL, (byte)3)); // Голубая шерсть
        map.put(new Color(248, 198, 39), new BlockData(Material.WOOL, (byte)4)); // Желтая шерсть
        map.put(new Color(112, 185, 25), new BlockData(Material.WOOL, (byte)5)); // Лаймовая шерсть
        map.put(new Color(237, 141, 172), new BlockData(Material.WOOL, (byte)6)); // Розовая шерсть
        map.put(new Color(62, 68, 71), new BlockData(Material.WOOL, (byte)7)); // Серая шерсть
        map.put(new Color(142, 142, 134), new BlockData(Material.WOOL, (byte)8)); // Светло-серая шерсть
        map.put(new Color(21, 137, 145), new BlockData(Material.WOOL, (byte)9)); // Бирюзовая шерсть
        map.put(new Color(121, 42, 172), new BlockData(Material.WOOL, (byte)10)); // Фиолетовая шерсть
        map.put(new Color(53, 57, 157), new BlockData(Material.WOOL, (byte)11)); // Синяя шерсть
        map.put(new Color(114, 71, 40), new BlockData(Material.WOOL, (byte)12)); // Коричневая шерсть
        map.put(new Color(84, 109, 27), new BlockData(Material.WOOL, (byte)13)); // Зеленая шерсть
        map.put(new Color(161, 39, 34), new BlockData(Material.WOOL, (byte)14)); // Красная шерсть
        map.put(new Color(20, 21, 25), new BlockData(Material.WOOL, (byte)15)); // Черная шерсть

        // Разновидности камней
        map.put(new Color(136, 136, 136), new BlockData(Material.STONE, (byte)1)); // Гранит
        map.put(new Color(153, 153, 153), new BlockData(Material.STONE, (byte)3)); // Диорит
        map.put(new Color(131, 131, 131), new BlockData(Material.STONE, (byte)5)); // Андезит
        map.put(new Color(115, 115, 115), new BlockData(Material.STONE, (byte)2)); // Полированный гранит
        map.put(new Color(165, 165, 165), new BlockData(Material.STONE, (byte)4)); // Полированный диорит
        map.put(new Color(135, 135, 135), new BlockData(Material.STONE, (byte)6)); // Полированный андезит
        map.put(new Color(120, 70, 70), new BlockData(Material.NETHER_BRICK)); // Адский кирпич
        map.put(new Color(45, 23, 27), new BlockData(Material.RED_NETHER_BRICK)); // Красный адский кирпич

        // Разновидности деревянных блоков
        map.put(new Color(196, 179, 123), new BlockData(Material.WOOD, (byte)2)); // Березовое дерево
        map.put(new Color(158, 116, 77), new BlockData(Material.WOOD, (byte)4)); // Акация
        map.put(new Color(67, 43, 26), new BlockData(Material.WOOD, (byte)5)); // Темный дуб
        map.put(new Color(168, 90, 50), new BlockData(Material.LOG, (byte)0)); // Дубовое бревно
        map.put(new Color(116, 91, 59), new BlockData(Material.LOG, (byte)1)); // Еловое бревно
        map.put(new Color(188, 175, 133), new BlockData(Material.LOG, (byte)2)); // Березовое бревно
        map.put(new Color(172, 132, 85), new BlockData(Material.LOG, (byte)3)); // Джунглевое бревно
        map.put(new Color(154, 110, 77), new BlockData(Material.LOG_2, (byte)0)); // Акациевое бревно
        map.put(new Color(68, 50, 31), new BlockData(Material.LOG_2, (byte)1)); // Бревно темного дуба

        // Разновидности блоков земли
        map.put(new Color(189, 151, 110), new BlockData(Material.SAND)); // Песок
        map.put(new Color(215, 195, 161), new BlockData(Material.SAND, (byte)1)); // Красный песок
        map.put(new Color(125, 82, 82), new BlockData(Material.NETHERRACK)); // Адский камень
        map.put(new Color(132, 95, 67), new BlockData(Material.DIRT, (byte)1)); // Подзол
        map.put(new Color(174, 134, 98), new BlockData(Material.DIRT, (byte)2)); // Дёрн
        map.put(new Color(135, 97, 67), new BlockData(Material.SOUL_SAND)); // Песок душ
        map.put(new Color(221, 224, 165), new BlockData(Material.ENDER_STONE)); // Эндерняк
        map.put(new Color(193, 143, 144), new BlockData(Material.END_BRICKS)); // Кирпичи Края

        // Блоки Бетона (добавлены в 1.12)
        map.put(new Color(207, 213, 214), new BlockData(Material.CONCRETE, (byte)0)); // Белый бетон
        map.put(new Color(224, 97, 1), new BlockData(Material.CONCRETE, (byte)1)); // Оранжевый бетон
        map.put(new Color(169, 48, 159), new BlockData(Material.CONCRETE, (byte)2)); // Пурпурный бетон
        map.put(new Color(36, 137, 199), new BlockData(Material.CONCRETE, (byte)3)); // Голубой бетон
        map.put(new Color(241, 175, 21), new BlockData(Material.CONCRETE, (byte)4)); // Желтый бетон
        map.put(new Color(94, 169, 24), new BlockData(Material.CONCRETE, (byte)5)); // Лаймовый бетон
        map.put(new Color(214, 101, 143), new BlockData(Material.CONCRETE, (byte)6)); // Розовый бетон
        map.put(new Color(55, 58, 62), new BlockData(Material.CONCRETE, (byte)7)); // Серый бетон
        map.put(new Color(125, 125, 115), new BlockData(Material.CONCRETE, (byte)8)); // Светло-серый бетон
        map.put(new Color(21, 119, 136), new BlockData(Material.CONCRETE, (byte)9)); // Бирюзовый бетон
        map.put(new Color(100, 32, 156), new BlockData(Material.CONCRETE, (byte)10)); // Фиолетовый бетон
        map.put(new Color(45, 47, 143), new BlockData(Material.CONCRETE, (byte)11)); // Синий бетон
        map.put(new Color(96, 60, 32), new BlockData(Material.CONCRETE, (byte)12)); // Коричневый бетон
        map.put(new Color(73, 91, 36), new BlockData(Material.CONCRETE, (byte)13)); // Зеленый бетон
        map.put(new Color(142, 33, 33), new BlockData(Material.CONCRETE, (byte)14)); // Красный бетон
        map.put(new Color(8, 10, 15), new BlockData(Material.CONCRETE, (byte)15)); // Черный бетон

        // Дополнительные цвета окрашенной глины (Stained Clay/Terracotta)
        map.put(new Color(209, 178, 161), new BlockData(Material.STAINED_CLAY, (byte)0)); // Белая глина
        map.put(new Color(210, 100, 70), new BlockData(Material.STAINED_CLAY, (byte)1)); // Оранжевая глина (уже есть)
        map.put(new Color(162, 84, 138), new BlockData(Material.STAINED_CLAY, (byte)2)); // Пурпурная глина
        map.put(new Color(113, 108, 138), new BlockData(Material.STAINED_CLAY, (byte)3)); // Светло-синяя глина
        map.put(new Color(186, 133, 35), new BlockData(Material.STAINED_CLAY, (byte)4)); // Желтая глина
        map.put(new Color(95, 169, 24), new BlockData(Material.STAINED_CLAY, (byte)5)); // Зеленая глина (уже есть)
        map.put(new Color(161, 78, 78), new BlockData(Material.STAINED_CLAY, (byte)6)); // Розовая глина
        map.put(new Color(58, 42, 36), new BlockData(Material.STAINED_CLAY, (byte)7)); // Серая глина
        map.put(new Color(135, 107, 98), new BlockData(Material.STAINED_CLAY, (byte)8)); // Светло-серая глина
        map.put(new Color(86, 91, 91), new BlockData(Material.STAINED_CLAY, (byte)9)); // Бирюзовая глина
        map.put(new Color(118, 70, 86), new BlockData(Material.STAINED_CLAY, (byte)10)); // Фиолетовая глина
        map.put(new Color(74, 60, 91), new BlockData(Material.STAINED_CLAY, (byte)11)); // Синяя глина
        map.put(new Color(77, 51, 36), new BlockData(Material.STAINED_CLAY, (byte)12)); // Коричневая глина
        map.put(new Color(76, 83, 42), new BlockData(Material.STAINED_CLAY, (byte)13)); // Зеленая глина
        map.put(new Color(143, 61, 47), new BlockData(Material.STAINED_CLAY, (byte)14)); // Красная глина
        map.put(new Color(37, 23, 16), new BlockData(Material.STAINED_CLAY, (byte)15)); // Черная глина

        // Прочие декоративные блоки
        map.put(new Color(104, 118, 129), new BlockData(Material.PRISMARINE)); // Призмарин
        map.put(new Color(103, 146, 134), new BlockData(Material.PRISMARINE, (byte)1)); // Призмариновый кирпич
        map.put(new Color(67, 84, 74), new BlockData(Material.PRISMARINE, (byte)2)); // Темный призмарин
        map.put(new Color(167, 167, 167), new BlockData(Material.SEA_LANTERN)); // Морской фонарь
        map.put(new Color(100, 48, 100), new BlockData(Material.PURPUR_BLOCK)); // Пурпуровый блок
        map.put(new Color(98, 46, 98), new BlockData(Material.PURPUR_PILLAR)); // Пурпуровая колонна
        map.put(new Color(165, 247, 29), new BlockData(Material.SLIME_BLOCK)); // Слизистый блок
        map.put(new Color(83, 220, 209), new BlockData(Material.BEACON)); // Маяк
        map.put(new Color(43, 30, 24), new BlockData(Material.MONSTER_EGGS)); // Заражённый камень

        return map;
    }

    public ModelBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Строит модель в мире Minecraft на основе OBJ и MTL файлов
     */
    public CompletableFuture<Void> buildFromModel(File modelFile, Location location, Vector dimensions, Consumer<Double> progressCallback) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Чтение модели из OBJ-файла
                List<float[]> vertices = new ArrayList<>();
                List<int[]> faces = new ArrayList<>();
                List<float[]> texCoords = new ArrayList<>();  // UV-координаты
                List<int[]> texFaces = new ArrayList<>();     // Индексы текстурных координат

                // Пути к файлам
                File mtlFile = null;
                File textureFile = null;

                plugin.getLogger().info("Загрузка модели из файла: " + modelFile.getAbsolutePath());

                // Чтение OBJ файла
                try (BufferedReader reader = new BufferedReader(new FileReader(modelFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("v ")) {
                            // Вершина
                            String[] parts = line.split(" ");
                            if (parts.length >= 4) {
                                float x = Float.parseFloat(parts[1]);
                                float y = Float.parseFloat(parts[2]);
                                float z = Float.parseFloat(parts[3]);
                                vertices.add(new float[]{x, y, z});
                            }
                        } else if (line.startsWith("vt ")) {
                            // Текстурная координата
                            String[] parts = line.split(" ");
                            if (parts.length >= 3) {
                                float u = Float.parseFloat(parts[1]);
                                float v = parts.length > 2 ? Float.parseFloat(parts[2]) : 0;
                                texCoords.add(new float[]{u, v});
                            }
                        } else if (line.startsWith("f ")) {
                            // Грань
                            String[] parts = line.split(" ");
                            if (parts.length >= 4) {
                                int[] face = new int[3];
                                int[] texFace = new int[3];

                                for (int i = 0; i < 3; i++) {
                                    String[] indices = parts[i+1].split("/");
                                    face[i] = Integer.parseInt(indices[0]) - 1; // OBJ индексы с 1

                                    if (indices.length > 1 && !indices[1].isEmpty()) {
                                        texFace[i] = Integer.parseInt(indices[1]) - 1;
                                    } else {
                                        texFace[i] = 0; // По умолчанию
                                    }
                                }

                                faces.add(face);
                                texFaces.add(texFace);
                            }
                        } else if (line.startsWith("mtllib ")) {
                            // Файл материалов
                            String mtlFileName = line.substring(7).trim();
                            mtlFile = new File(modelFile.getParent(), mtlFileName);
                            plugin.getLogger().info("Найден MTL: " + mtlFile.getAbsolutePath());
                        }
                    }
                }

                plugin.getLogger().info("Загружено вершин: " + vertices.size() +
                        ", граней: " + faces.size() +
                        ", текстурных координат: " + texCoords.size());

                // Если есть MTL файл, ищем в нем текстуру
                if (mtlFile != null && mtlFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(mtlFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("map_Kd ")) {
                                String textureName = line.substring(7).trim();
                                textureFile = new File(modelFile.getParent(), textureName);
                                plugin.getLogger().info("Найдена текстура: " + textureFile.getAbsolutePath());
                                break;
                            }
                        }
                    }
                }

                // Находим минимальные и максимальные координаты модели
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

                for (float[] v : vertices) {
                    minX = Math.min(minX, v[0]);
                    minY = Math.min(minY, v[1]);
                    minZ = Math.min(minZ, v[2]);
                    maxX = Math.max(maxX, v[0]);
                    maxY = Math.max(maxY, v[1]);
                    maxZ = Math.max(maxZ, v[2]);
                }

                // Размеры модели
                float modelWidth = maxX - minX;
                float modelHeight = maxY - minY;
                float modelDepth = maxZ - minZ;

                // Размеры региона
                int regionWidth = (int) dimensions.getX();
                int regionHeight = (int) dimensions.getY();
                int regionDepth = (int) dimensions.getZ();

                // Определяем масштаб для помещения в регион
                float scaleX = (regionWidth - 2) / modelWidth;
                float scaleY = (regionHeight - 2) / modelHeight;
                float scaleZ = (regionDepth - 2) / modelDepth;

                // Берем минимальный масштаб для сохранения пропорций
                float scale = Math.min(Math.min(scaleX, scaleY), scaleZ) * 0.9f; // 90% для запаса

                plugin.getLogger().info("Масштаб: " + scale + " (wx" + scaleX + ", hy" + scaleY + ", dz" + scaleZ + ")");

                // Создаем массивы для блоков и материалов
                boolean[][][] voxels = new boolean[regionWidth][regionHeight][regionDepth];
                Material[][][] materials = new Material[regionWidth][regionHeight][regionDepth];
                byte[][][] dataValues = new byte[regionWidth][regionHeight][regionDepth];

                // Центрирование модели в регионе
                int offsetX = (regionWidth - (int)(modelWidth * scale)) / 2;
                int offsetY = (regionHeight - (int)(modelHeight * scale)) / 2;
                int offsetZ = (regionDepth - (int)(modelDepth * scale)) / 2;

                // Загружаем текстуру, если есть
                BufferedImage texture = null;
                if (textureFile != null && textureFile.exists()) {
                    try {
                        texture = ImageIO.read(textureFile);
                        plugin.getLogger().info("Текстура загружена: " +
                                (texture != null ? texture.getWidth() + "x" + texture.getHeight() : "не удалось"));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка при загрузке текстуры: " + e.getMessage());
                    }
                }

                // Заполняем вокселы
                for (int i = 0; i < faces.size(); i++) {
                    int[] face = faces.get(i);
                    int[] texFace = texFaces.size() > i ? texFaces.get(i) : new int[]{0, 0, 0};

                    // Получаем вершины грани
                    float[] v1 = vertices.get(face[0]);
                    float[] v2 = vertices.get(face[1]);
                    float[] v3 = vertices.get(face[2]);

                    // Преобразуем координаты в обычной ориентации (без поворота)
                    int x1 = (int)((v1[0] - minX) * scale) + offsetX;
                    int y1 = (int)((v1[1] - minY) * scale) + offsetY;
                    int z1 = (int)((v1[2] - minZ) * scale) + offsetZ;

                    int x2 = (int)((v2[0] - minX) * scale) + offsetX;
                    int y2 = (int)((v2[1] - minY) * scale) + offsetY;
                    int z2 = (int)((v2[2] - minZ) * scale) + offsetZ;

                    int x3 = (int)((v3[0] - minX) * scale) + offsetX;
                    int y3 = (int)((v3[1] - minY) * scale) + offsetY;
                    int z3 = (int)((v3[2] - minZ) * scale) + offsetZ;

                    // Проверяем границы
                    x1 = Math.min(Math.max(x1, 0), regionWidth - 1);
                    y1 = Math.min(Math.max(y1, 0), regionHeight - 1);
                    z1 = Math.min(Math.max(z1, 0), regionDepth - 1);

                    x2 = Math.min(Math.max(x2, 0), regionWidth - 1);
                    y2 = Math.min(Math.max(y2, 0), regionHeight - 1);
                    z2 = Math.min(Math.max(z2, 0), regionDepth - 1);

                    x3 = Math.min(Math.max(x3, 0), regionWidth - 1);
                    y3 = Math.min(Math.max(y3, 0), regionHeight - 1);
                    z3 = Math.min(Math.max(z3, 0), regionDepth - 1);

                    // Получаем цвет из текстуры для этой грани
                    Material material = Material.STONE; // По умолчанию
                    byte data = 0;

                    if (texture != null && texCoords.size() > 0) {
                        // Берем среднюю UV-координату для грани
                        float avgU = 0, avgV = 0;
                        for (int j = 0; j < 3; j++) {
                            if (texFace[j] < texCoords.size()) {
                                float[] tc = texCoords.get(texFace[j]);
                                avgU += tc[0];
                                avgV += tc[1];
                            }
                        }
                        avgU /= 3;
                        avgV /= 3;

                        // Получаем цвет из текстуры
                        int texX = Math.min(Math.max((int)(avgU * texture.getWidth()), 0), texture.getWidth() - 1);
                        int texY = Math.min(Math.max((int)((1.0f - avgV) * texture.getHeight()), 0), texture.getHeight() - 1);

                        try {
                            Color color = new Color(texture.getRGB(texX, texY));
                            BlockData blockData = findClosestMaterial(color);
                            material = blockData.material;
                            data = blockData.data;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Ошибка при определении цвета: " + e.getMessage());
                            material = getDefaultMaterialByHeight(y1);
                        }
                    } else {
                        // Определение материала на основе высоты
                        material = getDefaultMaterialByHeight(y1);
                    }

                    // Заполняем вокселы
                    voxels[x1][y1][z1] = true;
                    materials[x1][y1][z1] = material;
                    dataValues[x1][y1][z1] = data;

                    voxels[x2][y2][z2] = true;
                    materials[x2][y2][z2] = material;
                    dataValues[x2][y2][z2] = data;

                    voxels[x3][y3][z3] = true;
                    materials[x3][y3][z3] = material;
                    dataValues[x3][y3][z3] = data;

                    // Заполняем линии между вершинами для создания сплошной модели
                    fillLine(x1, y1, z1, x2, y2, z2, voxels, materials, dataValues, material, data, regionWidth, regionHeight, regionDepth);
                    fillLine(x2, y2, z2, x3, y3, z3, voxels, materials, dataValues, material, data, regionWidth, regionHeight, regionDepth);
                    fillLine(x3, y3, z3, x1, y1, z1, voxels, materials, dataValues, material, data, regionWidth, regionHeight, regionDepth);
                }

                // Размещаем блоки в мире Minecraft
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int totalBlocks = 0;

                    // Считаем количество блоков
                    for (int x = 0; x < regionWidth; x++) {
                        for (int y = 0; y < regionHeight; y++) {
                            for (int z = 0; z < regionDepth; z++) {
                                if (voxels[x][y][z]) {
                                    totalBlocks++;
                                }
                            }
                        }
                    }

                    final int[] blockCounter = {0};
                    int blocksPerBatch = Math.max(1, totalBlocks / 50); // Разбиваем на партии

                    for (int x = 0; x < regionWidth; x++) {
                        for (int y = 0; y < regionHeight; y++) {
                            for (int z = 0; z < regionDepth; z++) {
                                if (voxels[x][y][z]) {
                                    final int fx = x, fy = y, fz = z;
                                    final Material material = materials[x][y][z];
                                    final byte data = dataValues[x][y][z];

                                    int finalTotalBlocks = totalBlocks;
                                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                        Block block = location.getWorld().getBlockAt(
                                                location.getBlockX() + fx,
                                                location.getBlockY() + fy,
                                                location.getBlockZ() + fz
                                        );

                                        // Устанавливаем блок и его data value
                                        block.setType(material);

                                        if (data != 0) {
                                            try {
                                                org.bukkit.block.BlockState state = block.getState();
                                                state.setRawData(data);
                                                state.update(true);
                                            } catch (Exception e) {
                                                plugin.getLogger().warning("Не удалось установить data value для блока: " + e.getMessage());
                                            }
                                        }

                                        // Обновляем прогресс
                                        blockCounter[0]++;
                                        double progress = (double) blockCounter[0] / finalTotalBlocks;
                                        if (blockCounter[0] % blocksPerBatch == 0 || blockCounter[0] == finalTotalBlocks) {
                                            progressCallback.accept(progress);
                                        }

                                        // Завершаем, когда все блоки размещены
                                        if (blockCounter[0] >= finalTotalBlocks) {
                                            future.complete(null);
                                        }
                                    }, blockCounter[0] / 50); // До 50 блоков за тик
                                }
                            }
                        }
                    }

                    // Если нет блоков для размещения
                    if (totalBlocks == 0) {
                        future.complete(null);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при построении модели: " + e.getMessage());
                e.printStackTrace();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    future.completeExceptionally(e);
                });
            }
        });

        return future;
    }

    /**
     * Заполняет линию между двумя точками в 3D пространстве
     */
    private void fillLine(int x1, int y1, int z1, int x2, int y2, int z2,
                          boolean[][][] voxels, Material[][][] materials, byte[][][] dataValues,
                          Material material, byte data,
                          int width, int height, int depth) {

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;

        int dm = Math.max(dx, Math.max(dy, dz));
        if (dm == 0) return;

        for (int i = 0; i <= dm; i++) {
            float t = (float) i / dm;
            int x = Math.round(x1 + t * (x2 - x1));
            int y = Math.round(y1 + t * (y2 - y1));
            int z = Math.round(z1 + t * (z2 - z1));

            if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
                voxels[x][y][z] = true;
                materials[x][y][z] = material;
                dataValues[x][y][z] = data;
            }
        }
    }
    /**
     * Находит ближайший материал к указанному цвету
     */
    private BlockData findClosestMaterial(Color targetColor) {
        BlockData closestBlock = new BlockData(Material.STONE); // По умолчанию
        double closestDistance = Double.MAX_VALUE;

        for (Map.Entry<Color, BlockData> entry : COLOR_TO_MATERIAL_MAP.entrySet()) {
            Color mapColor = entry.getKey();
            double distance = getColorDistance(targetColor, mapColor);

            if (distance < closestDistance) {
                closestDistance = distance;
                closestBlock = entry.getValue();
            }
        }

        return closestBlock;
    }

    /**
     * Вычисляет "расстояние" между двумя цветами
     */
    private double getColorDistance(Color c1, Color c2) {
        double rmean = (c1.getRed() + c2.getRed()) / 2.0;
        double r = c1.getRed() - c2.getRed();
        double g = c1.getGreen() - c2.getGreen();
        double b = c1.getBlue() - c2.getBlue();

        // Взвешенная формула расстояния по восприятию
        return Math.sqrt((2 + rmean/256) * r*r + 4 * g*g + (2 + (255-rmean)/256) * b*b);
    }

    /**
     * Определяет материал по высоте блока (для разнообразия)
     */
    private Material getDefaultMaterialByHeight(int y) {
        if (y < 5) {
            return Material.STONE;
        } else if (y < 10) {
            return Material.COBBLESTONE;
        } else if (y < 15) {
            return Material.BRICK;
        } else if (y < 20) {
            return Material.WOOD;
        } else if (y < 25) {
            return Material.WOOL; // Белая шерсть
        } else {
            return Material.QUARTZ_BLOCK;
        }
    }
}