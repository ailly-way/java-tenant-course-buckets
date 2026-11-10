package education.marketplace.service;

import education.marketplace.client.InfraiGateway;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CourseOrderHandoffTest {
    public static void main(String[] args) {
        RecordingGateway gateway = new RecordingGateway();
        CourseOrderHandoff workflow = new CourseOrderHandoff(gateway);

        var access = workflow.openSeller("Tutor-42");
        equal("learning-seller-tutor-42", access.bucket(), "seller receives its own deterministic bucket");
        equal("tenant-key-42", access.keyId(), "issued credential id is retained for offboarding");
        equal("one-time-value", access.plaintextKey(), "issued credential secret is returned for onboarding");

        var asset = workflow.storeSellerAsset("Tutor-42", "algebra-1", "worksheet.pdf",
                "lesson bytes".getBytes(StandardCharsets.UTF_8));
        equal("courses/algebra-1/assets/worksheet.pdf", asset.objectKey(), "seller asset stays in course namespace");

        gateway.found = false;
        equal("WAITING_FOR_ASSET", workflow.handoff("Tutor-42", "order-7", asset.objectKey()).state(),
                "missing asset keeps the order waiting");
        gateway.found = true;
        equal("READY_FOR_BUYER", workflow.handoff("Tutor-42", "order-7", asset.objectKey()).state(),
                "present asset makes the handoff ready");

        workflow.closeSeller("Tutor-42", access.keyId());
        equal(List.of("deleteBucket:learning-seller-tutor-42", "revokeTenantKey:tenant-key-42"),
                gateway.calls.subList(gateway.calls.size() - 2, gateway.calls.size()),
                "offboarding keeps bucket deletion and credential revocation together");
        System.out.println("PASS: bucket isolation, handoff state, and paired offboarding");
    }

    private static void equal(Object expected, Object actual, String lesson) {
        if (!expected.equals(actual)) throw new AssertionError(lesson + ": expected " + expected + ", got " + actual);
    }

    private static final class RecordingGateway implements InfraiGateway {
        private final List<String> calls = new ArrayList<>();
        private boolean found;

        public Map<String, Object> createBucket(String name) {
            calls.add("createBucket:" + name); return Map.of("name", name);
        }
        public Map<String, Object> putObject(String bucket, String key, String dataBase64, String idempotencyKey) {
            calls.add("putObject:" + bucket + "/" + key); return Map.of("key", key);
        }
        public Map<String, Object> headObject(String bucket, String key) {
            calls.add("headObject:" + bucket + "/" + key); return Map.of("found", found);
        }
        public Map<String, Object> presignDownload(String bucket, String key, int expiresSeconds, String idempotencyKey) {
            calls.add("presignDownload:" + bucket + "/" + key); return Map.of("url", "https://download.example/order-7");
        }
        public Map<String, Object> createTenantKey(String name, String idempotencyKey) {
            calls.add("createTenantKey:" + name); return Map.of("key_id", "tenant-key-42", "key_secret", "one-time-value");
        }
        public Map<String, Object> deleteBucket(String bucket) {
            calls.add("deleteBucket:" + bucket); return Map.of();
        }
        public Map<String, Object> revokeTenantKey(String keyId) {
            calls.add("revokeTenantKey:" + keyId); return Map.of();
        }
    }
}
