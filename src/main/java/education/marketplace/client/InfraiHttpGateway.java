package education.marketplace.client;

import education.marketplace.config.MarketplaceConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiHttpGateway implements InfraiGateway {
    private final HttpClient http;
    private final MarketplaceConfig config;

    public InfraiHttpGateway(MarketplaceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public Map<String, Object> createBucket(String name) {
        return call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    @Override
    public Map<String, Object> putObject(String bucket, String key, String dataBase64, String idempotencyKey) {
        return call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + segment(key),
                Map.of("data_base64", dataBase64, "idempotency_key", idempotencyKey));
    }

    @Override
    public Map<String, Object> headObject(String bucket, String key) {
        return call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + segment(key), null);
    }

    @Override
    public Map<String, Object> presignDownload(String bucket, String key, int expiresSeconds, String idempotencyKey) {
        return call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key),
                Map.of("op", "get", "expires_seconds", expiresSeconds, "idempotency_key", idempotencyKey));
    }

    @Override
    public Map<String, Object> createTenantKey(String name, String idempotencyKey) {
        return call("POST", "/v1/account/keys/create",
                Map.of("name", name, "idempotency_key", idempotencyKey));
    }

    @Override
    public Map<String, Object> deleteBucket(String bucket) {
        return call("DELETE", "/v1/storage/bucket/delete/" + segment(bucket), null);
    }

    @Override
    public Map<String, Object> revokeTenantKey(String keyId) {
        return call("DELETE", "/v1/account/keys/revoke/" + segment(keyId), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(body)));
            try {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, Object> envelope = JsonCodec.object(response.body());
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                            ? (Map<String, Object>) value : Map.of("message", "Request rejected");
                    if (response.statusCode() == 429 && attempt < 3) {
                        pause(retryDelay(response, attempt));
                        continue;
                    }
                    throw new InfraiException(response.statusCode(), error);
                }
                Object data = envelope.get("data");
                if (data == null) return Map.of();
                return data instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of("value", data);
            } catch (IOException error) {
                throw new IllegalStateException("Could not reach Infrai", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", error);
            }
        }
        throw new IllegalStateException("Retry loop ended unexpectedly");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Math.max(1L, Long.parseLong(value)) * 1_000L; }
                    catch (NumberFormatException ignored) { return 1_000L << attempt; }
                }).orElse(1_000L << attempt);
    }

    private static void pause(long milliseconds) throws InterruptedException { Thread.sleep(milliseconds); }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static final class InfraiException extends RuntimeException {
        private final int status;
        private final Map<String, Object> error;

        private InfraiException(int status, Map<String, Object> error) {
            super(String.valueOf(error.getOrDefault("message", error.getOrDefault("code", "Request rejected"))));
            this.status = status;
            this.error = Map.copyOf(error);
        }

        public int status() { return status; }
        public Map<String, Object> error() { return error; }
    }
}
