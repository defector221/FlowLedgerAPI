package com.flowledger.search.config;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class OpenSearchClientHolder {
    private final SearchProperties properties;
    private OpenSearchTransport transport;
    private OpenSearchClient client;
    private volatile boolean available;

    public OpenSearchClientHolder(SearchProperties properties) {
        this.properties = properties;
        initialize();
    }

    private void initialize() {
        if (!properties.isEnabled()) {
            log.info("FlowLedger search is disabled; OpenSearch client will not be created");
            return;
        }
        try {
            HttpHost host = toHttpHost(properties.getUrl());
            ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);

            if (StringUtils.hasText(properties.getUsername()) && properties.getPassword() != null) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(host),
                        new UsernamePasswordCredentials(
                                properties.getUsername(),
                                properties.getPassword().toCharArray()));
                builder.setHttpClientConfigCallback(
                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
            }

            if ("https".equalsIgnoreCase(host.getSchemeName()) && !properties.isSslVerify()) {
                SSLContext sslContext = SSLContextBuilder.create()
                        .loadTrustMaterial(null, (chains, authType) -> true)
                        .build();
                builder.setHttpClientConfigCallback(httpClientBuilder -> {
                    if (StringUtils.hasText(properties.getUsername()) && properties.getPassword() != null) {
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                                new AuthScope(host),
                                new UsernamePasswordCredentials(
                                        properties.getUsername(),
                                        properties.getPassword().toCharArray()));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return httpClientBuilder.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                            .setTlsStrategy(ClientTlsStrategyBuilder.create()
                                    .setSslContext(sslContext)
                                    .build())
                            .build());
                });
            }

            transport = builder.build();
            client = new OpenSearchClient(transport);
            client.ping();
            available = true;
            log.info("Connected to OpenSearch at {}", properties.getUrl());
        } catch (Exception ex) {
            available = false;
            client = null;
            closeQuietly();
            log.warn(
                    "OpenSearch unavailable at {}: {}. Search endpoints will return 503 until it recovers.",
                    properties.getUrl(),
                    ex.getMessage());
        }
    }

    private HttpHost toHttpHost(String url) {
        URI uri = URI.create(url.trim());
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid OpenSearch URL: " + url);
        }
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 9200);
        return new HttpHost(scheme, uri.getHost(), port);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isAvailable() {
        return properties.isEnabled() && available && client != null;
    }

    public OpenSearchClient client() {
        return client;
    }

    public String index() {
        return properties.getIndex();
    }

    public void markUnavailable(String reason) {
        available = false;
        log.warn("OpenSearch marked unavailable: {}", reason);
    }

    @PreDestroy
    void destroy() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception ignored) {
                // ignore
            }
            transport = null;
        }
    }
}
