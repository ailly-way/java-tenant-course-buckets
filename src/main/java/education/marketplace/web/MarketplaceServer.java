package education.marketplace.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import education.marketplace.config.MarketplaceConfig;
import education.marketplace.client.InfraiHttpGateway;
import education.marketplace.client.JsonCodec;
import education.marketplace.service.CourseOrderHandoff;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketplaceServer {
    private MarketplaceServer() {}

    public static void main(String[] args) throws IOException {
        MarketplaceConfig config = MarketplaceConfig.fromEnvironment();
        CourseOrderHandoff workflow = new CourseOrderHandoff(new InfraiHttpGateway(config));
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/sellers/open", exchange -> route(exchange, "POST", body -> {
            var result = workflow.openSeller(text(body, "sellerId"));
            return map("sellerId", result.sellerId(), "bucket", result.bucket(), "keyId", result.keyId(),
                    "plaintextKey", result.plaintextKey());
        }));
        server.createContext("/assets/seller", exchange -> route(exchange, "POST", body -> {
            var result = workflow.storeSellerAsset(text(body, "sellerId"), text(body, "courseId"),
                    text(body, "filename"), text(body, "content").getBytes(StandardCharsets.UTF_8));
            return map("bucket", result.bucket(), "objectKey", result.objectKey(), "state", result.state());
        }));
        server.createContext("/updates/buyer", exchange -> route(exchange, "POST", body -> {
            var result = workflow.storeBuyerUpdate(text(body, "sellerId"), text(body, "orderId"),
                    text(body, "updateId"), JsonCodec.stringify(body).getBytes(StandardCharsets.UTF_8));
            return map("bucket", result.bucket(), "objectKey", result.objectKey(), "state", result.state());
        }));
        server.createContext("/orders/handoff", exchange -> route(exchange, "POST", body -> {
            var result = workflow.handoff(text(body, "sellerId"), text(body, "orderId"), text(body, "assetKey"));
            return map("orderId", result.orderId(), "state", result.state(), "downloadUrl", result.downloadUrl());
        }));
        server.createContext("/sellers/close", exchange -> route(exchange, "POST", body -> {
            var result = workflow.closeSeller(text(body, "sellerId"), text(body, "keyId"));
            return map("sellerId", result.sellerId(), "bucket", result.bucket(), "keyId", result.keyId(),
                    "state", result.state());
        }));
        server.start();
        System.out.println("Marketplace learning service listening on http://localhost:" + config.port());
    }

    private static void route(HttpExchange exchange, String method, Handler handler) throws IOException {
        if (!method.equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> body = JsonCodec.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, handler.handle(body));
        } catch (InfraiHttpGateway.InfraiException error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            send(exchange, status, Map.of("error", error.error()));
        } catch (IllegalArgumentException error) {
            send(exchange, 400, Map.of("error", error.getMessage()));
        } catch (RuntimeException error) {
            send(exchange, 502, Map.of("error", error.getMessage()));
        }
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> payload) throws IOException {
        byte[] bytes = JsonCodec.stringify(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String text(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }

    @FunctionalInterface
    private interface Handler { Map<String, Object> handle(Map<String, Object> body); }
}
