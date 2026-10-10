package fi.lahtinen.kurssit.config;

import fi.lahtinen.kurssit.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Spring Data JPA Auditing (vaatimus 30).
 * <p>
 * {@link AuditorAware} kertoo auditoinnille, kuka on "nykyinen käyttäjä"; se täyttää
 * {@code @CreatedBy}- ja {@code @LastModifiedBy}-kentät. Aikaleimat (@CreatedDate/@LastModifiedDate)
 * auditointi täyttää itse.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SecurityUtils.currentUsernameOrSystem());
    }
}
