package com.saafhawa.ingest.http;

import com.saafhawa.common.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Polite upstream client (NFR-8): identifies via User-Agent with a contact URL. Per-source
 * concurrency limits and backoff are applied in the adapters that use this client.
 */
@Configuration
public class UpstreamWebClientConfig {

    @Bean
    public WebClient upstreamWebClient(AppProperties props) {
        String contact = props.contactUrl() == null ? "https://saafhawa.dev" : props.contactUrl();
        return WebClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "SaafHawa/0.1 (+" + contact + ")")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
    }
}
