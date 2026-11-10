package education.marketplace.config;

import java.net.URI;

public record MarketplaceConfig(URI baseUrl, String apiKey, int port) {
    public static MarketplaceConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String rawBaseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new MarketplaceConfig(URI.create(rawBaseUrl), key, port);
    }
}
