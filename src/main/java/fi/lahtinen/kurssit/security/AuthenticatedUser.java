package fi.lahtinen.kurssit.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Näkymien käyttämä apuluokka kirjautuneen käyttäjän tietoihin ja uloskirjautumiseen.
 * <p>
 * Käyttää Vaadinin {@link AuthenticationContext}ia, koska se osaa kirjautua ulos oikein myös
 * WebSocket/Push-yhteyden yli: tavallinen {@code /logout}-pyyntö ei mitätöisi Vaadin-sessiota
 * Push-tilassa luotettavasti.
 */
@Component
public class AuthenticatedUser {

    private final AuthenticationContext authenticationContext;
    private final AppUserRepository userRepository;

    public AuthenticatedUser(AuthenticationContext authenticationContext, AppUserRepository userRepository) {
        this.authenticationContext = authenticationContext;
        this.userRepository = userRepository;
    }

    /**
     * Hakee käyttäjän aina tietokannasta eikä säilytä sitä sessiossa: näin esim. vaihdettu
     * profiilikuva tai ylläpitäjän muuttamat roolit näkyvät heti ilman uudelleenkirjautumista.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> get() {
        return authenticationContext.getPrincipalName().flatMap(userRepository::findByUsername);
    }

    public boolean isLoggedIn() {
        return authenticationContext.isAuthenticated();
    }

    public void logout() {
        authenticationContext.logout();
    }
}
