package me.sworroo.runpod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RunPodModelGenerator {

    private final OkHttpClient client;
    private final String RUNPOD_API_KEY;
    private final String ENDPOINT_ID;
    private final String RUNPOD_API_URL;
    private final File dataFolder;

    // Таймауты и retry настройки
    private static final int MAX_POLL_ATTEMPTS = 120;
    private static final int POLL_INTERVAL_MS = 2000; // Увеличил до 2 сек для стабильности

    private final JsonParser parser = new JsonParser();

    public RunPodModelGenerator(String apiKey, String endpointId, File dataFolder) {
        this.RUNPOD_API_KEY = apiKey;
        this.ENDPOINT_ID = endpointId;
        this.RUNPOD_API_URL = "https://api.runpod.ai/v2/" + endpointId + "/run";
        this.dataFolder = dataFolder;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Генерация 3D модели по промпту
     */
    public CompletableFuture<ModelResult> generateModel(String prompt) {
        return generateModel(prompt, 15.0, 64, true);
    }

    /**
     * Генерация 3D модели с настраиваемыми параметрами
     */
    public CompletableFuture<ModelResult> generateModel(String prompt, double guidanceScale,
                                                        int numSteps, boolean includeZip) {
        CompletableFuture<ModelResult> future = new CompletableFuture<>();

        // Формируем тело запроса
        JsonObject input = new JsonObject();
        input.addProperty("prompt", prompt);
        input.addProperty("guidance_scale", guidanceScale);
        input.addProperty("num_steps", numSteps);
        input.addProperty("include_zip", includeZip);

        JsonObject requestBody = new JsonObject();
        requestBody.add("input", input);

        Request request = new Request.Builder()
                .url(RUNPOD_API_URL)
                .addHeader("Authorization", "Bearer " + RUNPOD_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()
                ))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(
                        new ModelGenerationException("Failed to connect to RunPod", e)
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                        future.completeExceptionally(
                                new ModelGenerationException("RunPod API error: " + response.code() + " - " + errorBody)
                        );
                        return;
                    }

                    String body = responseBody.string();
                    System.out.println("RunPod Initial Response: " + body);

                    JsonObject jsonResponse = parser.parse(body).getAsJsonObject();

                    if (!jsonResponse.has("id")) {
                        future.completeExceptionally(
                                new ModelGenerationException("No job ID in response")
                        );
                        return;
                    }

                    String jobId = jsonResponse.get("id").getAsString();
                    String status = jsonResponse.has("status")
                            ? jsonResponse.get("status").getAsString()
                            : "UNKNOWN";

                    // Если задача уже завершена (runsync endpoint)
                    if ("COMPLETED".equals(status) && jsonResponse.has("output")) {
                        processCompletedJob(jsonResponse, prompt, future);
                        return;
                    }

                    // Запускаем polling
                    pollJobStatus(jobId, prompt, future);

                } catch (Exception e) {
                    future.completeExceptionally(
                            new ModelGenerationException("Failed to parse response", e)
                    );
                }
            }
        });

        return future;
    }

    /**
     * Polling статуса задачи
     */
    private void pollJobStatus(String jobId, String prompt, CompletableFuture<ModelResult> future) {
        String statusUrl = "https://api.runpod.ai/v2/" + ENDPOINT_ID + "/status/" + jobId;

        CompletableFuture.runAsync(() -> {
            int attempts = 0;

            while (attempts < MAX_POLL_ATTEMPTS) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                    attempts++;

                    Request statusRequest = new Request.Builder()
                            .url(statusUrl)
                            .addHeader("Authorization", "Bearer " + RUNPOD_API_KEY)
                            .get()
                            .build();

                    try (Response statusResponse = client.newCall(statusRequest).execute()) {
                        if (!statusResponse.isSuccessful()) {
                            System.err.println("Status check failed: " + statusResponse.code());
                            continue;
                        }

                        String statusBody = statusResponse.body().string();
                        JsonObject statusJson = parser.parse(statusBody).getAsJsonObject();

                        String status = statusJson.has("status")
                                ? statusJson.get("status").getAsString()
                                : "UNKNOWN";

                        System.out.println("Job " + jobId + " status: " + status + " (attempt " + attempts + ")");

                        switch (status) {
                            case "COMPLETED":
                                processCompletedJob(statusJson, prompt, future);
                                return;

                            case "FAILED":
                                String error = extractError(statusJson);
                                future.completeExceptionally(new ModelGenerationException(error));
                                return;

                            case "CANCELLED":
                                future.completeExceptionally(
                                        new ModelGenerationException("Job was cancelled")
                                );
                                return;

                            case "IN_QUEUE":
                            case "IN_PROGRESS":
                                // Continue polling
                                break;

                            default:
                                System.out.println("Unknown status: " + status);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(
                            new ModelGenerationException("Polling interrupted", e)
                    );
                    return;
                } catch (Exception e) {
                    System.err.println("Error during polling: " + e.getMessage());
                }
            }

            future.completeExceptionally(
                    new ModelGenerationException("Job timed out after " + MAX_POLL_ATTEMPTS + " attempts")
            );
        });
    }

    /**
     * Извлечение ошибки из ответа
     */
    private String extractError(JsonObject json) {
        // Проверяем error на верхнем уровне
        if (json.has("error")) {
            return json.get("error").getAsString();
        }

        // Проверяем error внутри output
        if (json.has("output")) {
            JsonObject output = json.get("output").getAsJsonObject();
            if (output.has("error")) {
                return output.get("error").getAsString();
            }
            if (output.has("traceback")) {
                return output.get("traceback").getAsString();
            }
        }

        return "Unknown error";
    }

    /**
     * Обработка завершённой задачи
     */
    private void processCompletedJob(JsonObject jsonResponse, String prompt,
                                     CompletableFuture<ModelResult> future) {
        try {
            if (!jsonResponse.has("output")) {
                future.completeExceptionally(
                        new ModelGenerationException("No output in completed job")
                );
                return;
            }

            JsonObject output = jsonResponse.get("output").getAsJsonObject();

            // Проверяем статус внутри output (наш handler возвращает status)
            if (output.has("status")) {
                String outputStatus = output.get("status").getAsString();
                if ("error".equals(outputStatus)) {
                    String error = output.has("error")
                            ? output.get("error").getAsString()
                            : "Unknown error";
                    future.completeExceptionally(new ModelGenerationException(error));
                    return;
                }
            }

            // Проверяем наличие files
            if (!output.has("files")) {
                future.completeExceptionally(
                        new ModelGenerationException("No files in output")
                );
                return;
            }

            JsonObject files = output.get("files").getAsJsonObject();

            // Создаём директорию для модели
            String modelId = UUID.randomUUID().toString();
            File modelDir = new File(dataFolder, "temp_models/" + modelId);
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                future.completeExceptionally(
                        new ModelGenerationException("Failed to create model directory")
                );
                return;
            }

            ModelResult result = new ModelResult(modelId, modelDir, prompt);

            // Сохраняем OBJ файл (ключ "obj")
            if (files.has("obj")) {
                String objData = files.get("obj").getAsString();
                File objFile = new File(modelDir, "model.obj");
                saveBase64ToFile(objData, objFile);
                result.setObjFile(objFile);
            }

            // Сохраняем MTL файл (ключ "mtl")
            if (files.has("mtl")) {
                String mtlData = files.get("mtl").getAsString();
                File mtlFile = new File(modelDir, "model.mtl");
                saveBase64ToFile(mtlData, mtlFile);
                result.setMtlFile(mtlFile);
            }

            // Сохраняем текстуру (ключ "texture_png")
            if (files.has("texture_png")) {
                String textureData = files.get("texture_png").getAsString();
                File textureFile = new File(modelDir, "texture.png");
                saveBase64ToFile(textureData, textureFile);
                result.setTextureFile(textureFile);

                // Обновляем MTL чтобы указывал на правильное имя текстуры
                updateMtlTextureReference(result.getMtlFile(), "texture.png");
            }

            // Сохраняем превью (ключ "preview_png")
            if (files.has("preview_png")) {
                String previewData = files.get("preview_png").getAsString();
                File previewFile = new File(modelDir, "preview.png");
                saveBase64ToFile(previewData, previewFile);
                result.setPreviewFile(previewFile);
            }

            // Сохраняем ZIP архив (на уровне output, не внутри files)
            if (output.has("zip_archive")) {
                String zipData = output.get("zip_archive").getAsString();
                File zipFile = new File(modelDir, "model.zip");
                saveBase64ToFile(zipData, zipFile);
                result.setZipFile(zipFile);
            }

            if (result.getObjFile() != null) {
                System.out.println("Model generated successfully: " + modelDir.getAbsolutePath());
                future.complete(result);
            } else {
                future.completeExceptionally(
                        new ModelGenerationException("OBJ file not found in response. Available keys: " + files.entrySet())
                );
            }

        } catch (Exception e) {
            future.completeExceptionally(
                    new ModelGenerationException("Failed to process completed job", e)
            );
        }
    }

    /**
     * Обновление ссылки на текстуру в MTL файле
     */
    private void updateMtlTextureReference(File mtlFile, String textureName) {
        if (mtlFile == null || !mtlFile.exists()) return;

        try {
            String content = new String(java.nio.file.Files.readAllBytes(mtlFile.toPath()));
            // Заменяем имя текстуры на наше
            content = content.replaceAll("map_Kd\\s+\\S+", "map_Kd " + textureName);
            java.nio.file.Files.write(mtlFile.toPath(), content.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to update MTL texture reference: " + e.getMessage());
        }
    }

    /**
     * Сохранение Base64 данных в файл
     */
    private void saveBase64ToFile(String base64Data, File file) throws IOException {
        byte[] decodedData = Base64.getDecoder().decode(base64Data);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(decodedData);
        }
    }

    /**
     * Отмена задачи
     */
    public CompletableFuture<Boolean> cancelJob(String jobId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String cancelUrl = "https://api.runpod.ai/v2/" + ENDPOINT_ID + "/cancel/" + jobId;

        Request request = new Request.Builder()
                .url(cancelUrl)
                .addHeader("Authorization", "Bearer " + RUNPOD_API_KEY)
                .post(RequestBody.create(new byte[0], null))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) {
                future.complete(response.isSuccessful());
            }
        });

        return future;
    }

    /**
     * Проверка здоровья endpoint'а
     */
    public CompletableFuture<Boolean> healthCheck() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String healthUrl = "https://api.runpod.ai/v2/" + ENDPOINT_ID + "/health";

        Request request = new Request.Builder()
                .url(healthUrl)
                .addHeader("Authorization", "Bearer " + RUNPOD_API_KEY)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) {
                future.complete(response.isSuccessful());
            }
        });

        return future;
    }

    /**
     * Закрытие клиента
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    // ==================== Inner Classes ====================

    /**
     * Результат генерации модели
     */
    public static class ModelResult {
        private final String modelId;
        private final File modelDir;
        private final String prompt;
        private File objFile;
        private File mtlFile;
        private File textureFile;
        private File previewFile;
        private File zipFile;

        public ModelResult(String modelId, File modelDir, String prompt) {
            this.modelId = modelId;
            this.modelDir = modelDir;
            this.prompt = prompt;
        }

        // Getters
        public String getModelId() { return modelId; }
        public File getModelDir() { return modelDir; }
        public String getPrompt() { return prompt; }
        public File getObjFile() { return objFile; }
        public File getMtlFile() { return mtlFile; }
        public File getTextureFile() { return textureFile; }
        public File getPreviewFile() { return previewFile; }
        public File getZipFile() { return zipFile; }

        // Setters
        public void setObjFile(File objFile) { this.objFile = objFile; }
        public void setMtlFile(File mtlFile) { this.mtlFile = mtlFile; }
        public void setTextureFile(File textureFile) { this.textureFile = textureFile; }
        public void setPreviewFile(File previewFile) { this.previewFile = previewFile; }
        public void setZipFile(File zipFile) { this.zipFile = zipFile; }

        /**
         * Проверка наличия всех файлов модели
         */
        public boolean isComplete() {
            return objFile != null && objFile.exists();
        }

        /**
         * Проверка наличия текстур
         */
        public boolean hasTextures() {
            return mtlFile != null && mtlFile.exists()
                    && textureFile != null && textureFile.exists();
        }

        /**
         * Удаление всех файлов модели
         */
        public void cleanup() {
            if (modelDir != null && modelDir.exists()) {
                File[] files = modelDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) {
                            System.err.println("Failed to delete: " + file.getAbsolutePath());
                        }
                    }
                }
                if (!modelDir.delete()) {
                    System.err.println("Failed to delete directory: " + modelDir.getAbsolutePath());
                }
            }
        }

        @Override
        public String toString() {
            return "ModelResult{" +
                    "modelId='" + modelId + '\'' +
                    ", prompt='" + prompt + '\'' +
                    ", objFile=" + (objFile != null ? objFile.getName() : "null") +
                    ", mtlFile=" + (mtlFile != null ? mtlFile.getName() : "null") +
                    ", textureFile=" + (textureFile != null ? textureFile.getName() : "null") +
                    ", previewFile=" + (previewFile != null ? previewFile.getName() : "null") +
                    '}';
        }
    }

    /**
     * Исключение генерации модели
     */
    public static class ModelGenerationException extends Exception {
        public ModelGenerationException(String message) {
            super(message);
        }

        public ModelGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
