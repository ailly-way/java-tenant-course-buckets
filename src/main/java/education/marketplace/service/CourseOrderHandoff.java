package education.marketplace.service;

import education.marketplace.client.InfraiGateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class CourseOrderHandoff {
    private final InfraiGateway gateway;

    public CourseOrderHandoff(InfraiGateway gateway) { this.gateway = gateway; }

    public TenantAccess openSeller(String sellerId) {
        String bucket = bucketFor(sellerId);
        gateway.createBucket(bucket);
        Map<String, Object> credential = gateway.createTenantKey(
                "course-assets-" + sellerId, "seller-key-" + sellerId);
        return new TenantAccess(sellerId, bucket, required(credential, "key_id"), required(credential, "key_secret"));
    }

    public AssetReceipt storeSellerAsset(String sellerId, String courseId, String filename, byte[] content) {
        String bucket = bucketFor(sellerId);
        String objectKey = "courses/" + courseId + "/assets/" + filename;
        gateway.putObject(bucket, objectKey, Base64.getEncoder().encodeToString(content),
                "asset-" + sellerId + "-" + courseId + "-" + filename);
        return new AssetReceipt(bucket, objectKey, "SELLER_ASSET_STORED");
    }

    public AssetReceipt storeBuyerUpdate(String sellerId, String orderId, String updateId, byte[] content) {
        String bucket = bucketFor(sellerId);
        String objectKey = "orders/" + orderId + "/buyer-updates/" + updateId + ".json";
        gateway.putObject(bucket, objectKey, Base64.getEncoder().encodeToString(content),
                "buyer-update-" + orderId + "-" + updateId);
        return new AssetReceipt(bucket, objectKey, "BUYER_UPDATE_STORED");
    }

    public OrderDelivery handoff(String sellerId, String orderId, String assetKey) {
        String bucket = bucketFor(sellerId);
        Map<String, Object> head = gateway.headObject(bucket, assetKey);
        if (!Boolean.TRUE.equals(head.get("found"))) {
            return new OrderDelivery(orderId, "WAITING_FOR_ASSET", null);
        }
        Map<String, Object> signed = gateway.presignDownload(
                bucket, assetKey, 900, "handoff-" + orderId + "-" + assetKey);
        return new OrderDelivery(orderId, "READY_FOR_BUYER", required(signed, "url"));
    }

    public OffboardingReceipt closeSeller(String sellerId, String tenantKeyId) {
        String bucket = bucketFor(sellerId);
        gateway.deleteBucket(bucket);
        gateway.revokeTenantKey(tenantKeyId);
        return new OffboardingReceipt(sellerId, bucket, tenantKeyId, "CLOSED");
    }

    static String bucketFor(String sellerId) {
        String normalized = sellerId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        return "learning-seller-" + normalized;
    }

    private static String required(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Infrai response did not include " + field);
        }
        return text;
    }

    public record TenantAccess(String sellerId, String bucket, String keyId, String plaintextKey) {}
    public record AssetReceipt(String bucket, String objectKey, String state) {}
    public record OrderDelivery(String orderId, String state, String downloadUrl) {}
    public record OffboardingReceipt(String sellerId, String bucket, String keyId, String state) {}
}
