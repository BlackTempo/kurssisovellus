package fi.lahtinen.kurssit.security;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yhdistää Spring Securityn omaan {@link AppUser}-entiteettiin (vaatimus 16).
 * <p>
 * Spring Security vertaa kirjautumisessa annettua salasanaa tässä palautettuun BCrypt-tiivisteeseen
 * PasswordEncoderilla – selväkielistä salasanaa ei missään vaiheessa lueta tietokannasta.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public AppUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        // Sama virheilmoitus tuntemattomalle käyttäjälle ja väärälle salasanalle (Spring hoitaa
        // jälkimmäisen), jottei kirjautumislomakkeella voi selvittää olemassa olevia tunnuksia.
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Käyttäjää ei löydy"));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                // ROLE_-etuliite lisätään vasta tässä, jotta @RolesAllowed("ADMIN") toimii suoraan.
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList())
                .build();
    }
}
