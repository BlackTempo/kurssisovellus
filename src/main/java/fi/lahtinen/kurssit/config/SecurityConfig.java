package fi.lahtinen.kurssit.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import fi.lahtinen.kurssit.ui.views.auth.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security + Vaadin -integraatio (vaatimus 16).
 * <p>
 * {@link VaadinWebSecurity} tuo valmiiksi Vaadinin vaatimat poikkeukset (sisäiset pyynnöt, staattiset
 * resurssit, CSRF-käsittely) ja kytkee näkymien {@code @AnonymousAllowed}/{@code @PermitAll}/
 * {@code @RolesAllowed}-annotaatiot navigaation pääsynhallintaan. Näkymätason annotaatiot ovat
 * varsinainen pääsynhallinta; HTTP-tasolla avataan vain faviconin polku.
 * <p>
 * Huom: Vaadin 24.9+ merkitsee VaadinWebSecurityn vanhentuneeksi uuden VaadinSecurityConfigurerin
 * hyväksi. Tässä käytetään kurssimateriaalin mukaista, edelleen tuettua VaadinWebSecuritya.
 */
@Configuration
@EnableWebSecurity
@SuppressWarnings("deprecation")
public class SecurityConfig extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Favicon (META-INF/resources/icons) näytetään myös kirjautumissivulla, joten se avataan kaikille.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/icons/**")).permitAll());
        super.configure(http);
        setLoginView(http, LoginView.class);
    }

    /**
     * BCrypt: tarkoituksella hidas, suolattu tiivistefunktio. Oletustyökerroin 10 (2^10 kierrosta)
     * on riittävä tasapaino kirjautumisen nopeuden ja raa'an voiman vastustuskyvyn välillä.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
