package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Käyttäjien hallinta: rekisteröityminen, profiilikuva ja ylläpito (vaatimukset 16, 17, 19). */
@Service
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    /**
     * Luo uuden käyttäjän USER-roolilla. Rooli kovakoodataan tässä eikä lueta lomakkeelta, jotta
     * kukaan ei voi rekisteröityä ylläpitäjäksi muokkaamalla selaimen lähettämää dataa.
     */
    @Transactional
    public AppUser register(String username, String fullName, String email, String rawPassword) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("Käyttäjätunnus on jo käytössä");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Sähköpostiosoitteella on jo tunnus");
        }
        validatePassword(rawPassword);

        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(EnumSet.of(Role.USER));
        AppUser saved = userRepository.save(user);

        // Ylläpitäjän sähköposti-ilmoitus lähtee tapahtuman kautta vasta commitin jälkeen
        // (ks. MailService). Näin rekisteröinti ei riipu SMTP-palvelimen saatavuudesta.
        events.publishEvent(new UserRegisteredEvent(saved.getUsername(), saved.getFullName(), saved.getEmail()));
        return saved;
    }

    public void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Salasanan on oltava vähintään " + MIN_PASSWORD_LENGTH + " merkkiä");
        }
    }

    @Transactional
    public void changePassword(AppUser user, String rawPassword) {
        validatePassword(rawPassword);
        AppUser managed = userRepository.findById(user.getId()).orElseThrow();
        managed.setPasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Transactional
    public void updateProfileImage(String username, byte[] image, String contentType) {
        if (image.length > MAX_IMAGE_BYTES) {
            throw new BusinessException("Kuva on liian suuri (max 2 Mt)");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException("Vain PNG- ja JPEG-kuvat ovat sallittuja");
        }
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        user.setProfileImage(image);
        user.setProfileImageType(contentType);
    }

    @Transactional
    public void removeProfileImage(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setProfileImage(null);
            user.setProfileImageType(null);
        });
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    /** Ylläpitäjän tekemä roolien ja tilan muutos. */
    @Transactional
    public void updateRolesAndStatus(Long userId, Set<Role> roles, boolean enabled, String currentUsername) {
        if (roles.isEmpty()) {
            throw new BusinessException("Käyttäjällä on oltava vähintään yksi rooli");
        }
        AppUser user = userRepository.findById(userId).orElseThrow();
        // Ylläpitäjä ei voi lukita itseään ulos: viimeisen admin-oikeuden poisto omalta tunnukselta
        // jättäisi sovelluksen ilman ketään, joka voi korjata tilanteen käyttöliittymästä.
        if (user.getUsername().equals(currentUsername) && (!roles.contains(Role.ADMIN) || !enabled)) {
            throw new BusinessException("Et voi poistaa omaa ylläpitäjän oikeuttasi tai lukita omaa tunnustasi");
        }
        user.setRoles(EnumSet.copyOf(roles));
        user.setEnabled(enabled);
    }

    @Transactional
    public void delete(Long userId, String currentUsername) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        if (user.getUsername().equals(currentUsername)) {
            throw new BusinessException("Et voi poistaa omaa tunnustasi");
        }
        userRepository.delete(user);
    }
}
