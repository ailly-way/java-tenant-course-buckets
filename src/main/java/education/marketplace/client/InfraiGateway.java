package education.marketplace.client;

import java.util.Map;

public interface InfraiGateway {
    Map<String, Object> createBucket(String name);
    Map<String, Object> putObject(String bucket, String key, String dataBase64, String idempotencyKey);
    Map<String, Object> headObject(String bucket, String key);
    Map<String, Object> presignDownload(String bucket, String key, int expiresSeconds, String idempotencyKey);
    Map<String, Object> createTenantKey(String name, String idempotencyKey);
    Map<String, Object> deleteBucket(String bucket);
    Map<String, Object> revokeTenantKey(String keyId);
}
