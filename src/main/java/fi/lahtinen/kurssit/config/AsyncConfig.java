package fi.lahtinen.kurssit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ottaa käyttöön {@code @Async}-metodit. Sähköpostit lähetetään taustasäikeessä (vaatimukset 26–27),
 * jotta hidas tai saavuttamaton SMTP-palvelin ei jäädytä käyttöliittymää eikä kaada rekisteröitymistä.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
