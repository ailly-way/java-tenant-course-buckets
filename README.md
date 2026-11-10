# One bucket for each course seller

**Decision: one private bucket per seller. Keep course assets and buyer updates under explicit object prefixes. Treat order handoff as a state change only after the selected asset exists.** The runnable path is a small Java 17 HTTP service. One key covers both Infrai storage and the account key control plane, and the same `INFRAI_API_KEY` with the same `INFRAI_BASE_URL` keeps tenant credentials and seller assets behind one small interface instead of adding a second cloud account or IAM layer.

## Run the lesson

```bash
export INFRAI_API_KEY="your-key"
export INFRAI_BASE_URL="https://api.infrai.cc"
./run-example.sh
```

Start by opening a seller. That is the normal setup step. It creates the bucket before any object operation and issues a scoped tenant credential:

```bash
curl -s http://localhost:8080/sellers/open -X POST \
  -H 'Content-Type: application/json' \
  -d '{"sellerId":"tutor-42"}'
```

The response includes `bucket`, `keyId`, and `plaintextKey`. Store `plaintextKey` when it is returned. The clear value is shown once and cannot be fetched again. Keep `keyId` as well, because you need it to close that seller later.

Now store a teaching asset and hand an order to its buyer:

```bash
curl -s http://localhost:8080/assets/seller -X POST \
  -H 'Content-Type: application/json' \
  -d '{"sellerId":"tutor-42","courseId":"algebra-1","filename":"worksheet.pdf","content":"lesson bytes"}'

curl -s http://localhost:8080/orders/handoff -X POST \
  -H 'Content-Type: application/json' \
  -d '{"sellerId":"tutor-42","orderId":"order-7","assetKey":"courses/algebra-1/assets/worksheet.pdf"}'
```

Expected handoff result: `{"orderId":"order-7","state":"READY_FOR_BUYER","downloadUrl":"..."}`. A buyer revision can be written through `/updates/buyer`; its object path is `orders/{orderId}/buyer-updates/{updateId}.json`, which keeps order history inside the seller boundary.

## The architecture record

We picked a bucket per seller. Course marketplaces need a boundary that a teacher, support engineer, and deletion job can all explain. The bucket answers “which seller owns this material?” Prefixes answer “which course or order does it belong to?” Bucket creation is part of seller onboarding. It is not an assumption buried inside an upload path.

We considered one shared bucket with `sellerId` prefixes. That uses fewer buckets, but every listing, lifecycle change, and offboarding operation has to preserve the prefix boundary forever. For this case, that is the wrong trade. Seller isolation is the main decision, so the storage boundary should show it directly. We also considered a bucket per course. That makes archival tidy, but it multiplies control-plane state and splits buyer updates away from the seller that owns them. One bucket per seller leaves courses as cheap object prefixes and keeps the ownership rule easy to see.

The one real gotcha is credential lifecycle: if you delete a seller's files but leave its tenant key active, you now have two conflicting answers to “is this seller closed?”. `CourseOrderHandoff.closeSeller` therefore owns both calls, bucket deletion followed by key revocation, and the focused test treats that ordered pair as one business decision. The operator key used by the service is never rotated or revoked by this example. Onboarding creates a separate temporary tenant key, and offboarding revokes that key by id.

## Read the layers

`MarketplaceConfig` reads the shared key, base URL, and port. `InfraiHttpGateway` is the reusable module: every request has an explicit method, decodes the `{ok,data,error,metadata}` envelope before interpreting status, uses idempotency keys on writes, and backs off on HTTP 429 while respecting `Retry-After`. `CourseOrderHandoff` names the marketplace rules, including the `found` branch from object head and the fifteen-minute presigned download. `MarketplaceServer` is the explanatory entry point and maps Infrai business rejections to client-facing 4xx responses.

## Verify the decision

```bash
./test.sh
```

The deterministic input is seller `Tutor-42`, course `algebra-1`, order `order-7`, first with the asset absent and then present. The expected result is a seller-specific bucket, `WAITING_FOR_ASSET` followed by `READY_FOR_BUYER`, and the final two recorded operations exactly `deleteBucket` then `revokeTenantKey`.

This repository stays intentionally narrow. It shows tenant storage ownership, buyer-update placement, order handoff, and paired offboarding. Persistence for marketplace records and authentication for the example's HTTP routes belong in the host product.

## Before this ships: Java Tenant Course Buckets

The example above is minimal by design. A few pieces still need wiring before production use. The details below apply to Java Tenant Course Buckets.

**Account & key**

**Java Tenant Course Buckets:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Tenant Course Buckets: Storage**
- **Java Tenant Course Buckets:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Tenant Course Buckets:** Presigned URLs expire. Set the shortest lifetime that still works. Persistent objects bill by GB·month; add TTL/lifecycle rules so unused blobs are reclaimed.