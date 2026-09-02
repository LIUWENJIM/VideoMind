package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class EmbeddingUtils {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingUtils.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    /** Retry: 3 attempts, exponential backoff 500ms → 5s, retries on any RuntimeException. */
    private final Retry retry;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    public EmbeddingUtils(@Value("${ai.deepseek.api-key}") String apiKey,
                          @Value("${ai.deepseek.base-url}") String baseUrl,
                          @Value("${ai.embedding.model:BAAI/bge-m3}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;

        RetryConfig retryConfig = RetryConfig.<List<Double>>custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalBiFunction((attempt, resultOrException) -> {
                    if (resultOrException.isLeft()) {
                        return Math.min(500L * (1L << (attempt - 1)), 5_000L);
                    }
                    return 0L;
                })
                .retryOnException(e -> e instanceof RuntimeException)
                .build();
        this.retry = Retry.of("embedding", retryConfig);
        this.retry.getEventPublisher()
                .onRetry(event -> log.warn("embedding_retry attempt={} error={}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() != null
                                ? event.getLastThrowable().getMessage() : "null"));
    }

    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            return retry.executeCallable(() -> doEmbed(text));
        } catch (MaxRetriesExceededException e) {
            throw new IllegalStateException("Embedding 服务在 3 次重试后仍然失败", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 生成失败", e);
        }
    }

    private List<Double> doEmbed(String text) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", model);
            requestJson.put("input", text);

            Request request = new Request.Builder()
                    .url(baseUrl + "/embeddings")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(
                            requestJson.toString(),
                            MediaType.parse("application/json; charset=utf-8")
                    ))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int code = response.code();
                    // 4xx (except 429) are non-retriable
                    if (code >= 400 && code < 500 && code != 429) {
                        throw new IllegalArgumentException("Embedding API 返回客户端错误: " + code);
                    }
                    throw new IllegalStateException("Embedding API failed: " + code);
                }
                if (response.body() == null) {
                    throw new IllegalStateException("Embedding API returned empty body");
                }
                JSONObject json = JSON.parseObject(response.body().string());
                JSONArray data = json.getJSONArray("data");
                if (data == null || data.isEmpty()) throw new IllegalStateException("Embedding data is empty");
                JSONArray values = data.getJSONObject(0).getJSONArray("embedding");
                if (values == null || values.isEmpty()) throw new IllegalStateException("Embedding vector is empty");
                List<Double> embedding = new ArrayList<>(values.size());
                for (Object value : values) {
                    embedding.add(((Number) value).doubleValue());
                }
                return embedding;
            }
        } catch (IllegalArgumentException e) {
            // Non-retriable: propagate directly
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 生成失败", e);
        }
    }
}
