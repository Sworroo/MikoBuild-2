package me.sworroo.api;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ApiHandler {

    private final JavaPlugin plugin;
    private final String apiUrl;
    private final OkHttpClient client;

    public ApiHandler(JavaPlugin plugin, String apiUrl) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;

        // Установка увеличенного таймаута (10 минут)
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)  // Важно!
                .connectionPool(new ConnectionPool(5, 30, TimeUnit.MINUTES))  // Поддерживать соединения дольше
                .build();
    }

    public CompletableFuture<File> generateModel(String prompt, int size) {
        CompletableFuture<File> future = new CompletableFuture<>();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("size", size);

        Request request = new Request.Builder()
                .url(apiUrl + "/generate")
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()
                ))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    future.completeExceptionally(new IOException("Unexpected code " + response));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    System.out.println(responseBody);
                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                    if (jsonResponse.has("error")) {
                        future.completeExceptionally(new IOException(jsonResponse.get("error").getAsString()));
                        return;
                    }
                    File tempDir = new File(plugin.getDataFolder(), "temp_models/" + UUID.randomUUID().toString());
                    tempDir.mkdirs();

                    // Извлекаем данные OBJ файла из ответа
                    JsonArray fileObject = jsonResponse.getAsJsonArray("files");
                    File objOutputFile = null;
                    for (JsonElement jsonElement : fileObject) {
                        JsonObject file = jsonElement.getAsJsonObject();
                        String objName = file.get("name").getAsString();
                        String objData = file.get("data").getAsString();
                        File writer = new File(tempDir, objName);
                        try (FileOutputStream fos = new FileOutputStream(writer)) {
                            fos.write(Base64.getDecoder().decode(objData));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        if(objName.endsWith(".obj")){
                            objOutputFile = writer;
                        }
                    }
                    // Возвращаем OBJ файл
                    future.complete(objOutputFile);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }
}