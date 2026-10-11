package fi.lahtinen.kurssit.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
     * profiilikuva tai nimi näkyy heti. Huom: pääsynhallinta ei käytä tätä vaan kirjautumishetkellä
     * istuntoon tallennettuja rooleja ({@link #hasAnyRole}). Siksi ylläpitäjän tekemä roolimuutos
     * kirjaa käyttäjän ulos ({@link #isAccessRevoked}).
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> get() {
        return authenticationContext.getPrincipalName().flatMap(userRepository::findByUsername);
    }

    public boolean isLoggedIn() {
        return authenticationContext.isAuthenticated();
    }

    /** Tarkistaa istunnon roolit (samat, joihin @RolesAllowed perustuu). */
    public boolean hasAnyRole(Role... roles) {
        return authenticationContext.hasAnyRole(Arrays.stream(roles).map(Role::name).toList());
    }

    /**
     * Onko istunnon käyttöoikeus vanhentunut: tunnus on poistettu tai lukittu, tai sen roolit eivät
     * enää vastaa istunnon rooleja. Spring Security lukee roolit vain kirjautuessa, joten ilman tätä
     * tarkistusta lukittu käyttäjä voisi jatkaa jo avattua istuntoaan.
     */
    @Transactional(readOnly = true)
    public boolean isAccessRevoked() {
        if (!isLoggedIn()) {
            return false;
        }
        Set<String> sessionRoles = Set.copyOf(authenticationContext.getGrantedRoles());
        return get()
                .map(user -> !user.isEnabled() || !sessionRoles.equals(
                        user.getRoles().stream().map(Role::name).collect(Collectors.toSet())))
                .orElse(true);
    }

    public void logout() {
        authenticationContext.logout();
    }
}
