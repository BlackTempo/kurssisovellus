package fi.lahtinen.kurssit.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Staattinen pääsy kirjautuneen käyttäjän nimeen.
 * <p>
 * Tarvitaan paikoissa, joihin Spring ei voi injektoida beaneja: Enversin RevisionListener ja
 * Spring Data -auditoinnin AuditorAware. Kun muutos tehdään ilman kirjautunutta käyttäjää
 * (sovelluksen käynnistyksen demodata, rekisteröityminen, salasanan palautus), tekijäksi kirjataan
 * "system", jotta auditointisarakkeet eivät jää tyhjiksi ja historiasta näkee muutoksen lähteen.
 */
public final class SecurityUtils {

    public static final String SYSTEM_USER = "system";

    private SecurityUtils() {
    }

    public static String currentUsernameOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return SYSTEM_USER;
        }
        return auth.getName();
    }
}
