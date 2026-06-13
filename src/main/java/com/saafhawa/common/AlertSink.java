package com.saafhawa.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Pluggable ops alert sink (FR-1.2). Logs always; POSTs to a webhook if configured. Kept simple
 * and dependency-light so it can be swapped for PagerDuty/Slack later.
 */
@Component
public class AlertSink {

    private static final Logger log = LoggerFactory.getLogger(AlertSink.class);

    private final String webhookUrl;
    private final WebClient webClient = WebClient.builder().build();

    public AlertSink(AppProperties props) {
        this.webhookUrl = props.alerting().webhookUrl();
    }

    public void alert(String title, String detail) {
        log.error("ALERT: {} — {}", title, detail);
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                webClient.post().uri(webhookUrl)
                        .bodyValue(Map.of("title", title, "detail", detail))
                        .retrieve().toBodilessEntity().block();
            } catch (RuntimeException e) {
                log.warn("Failed to deliver alert webhook: {}", e.getMessage());
            }
        }
    }
}
