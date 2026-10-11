package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.PasswordResetToken;
import fi.lahtinen.kurssit.repository.PasswordResetTokenRepository;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Salasanan vaihto sähköpostilinkin avulla (vaatimus 27).
 * <p>
 * Kulku: käyttäjä antaa sähköpostiosoitteen → luodaan satunnainen token, josta tallennetaan vain
 * tiiviste → raakatoken lähetetään linkissä → linkin avaamisen jälkeen token tarkistetaan
 * (olemassa, voimassa, käyttämätön) ja salasana vaihdetaan. Vaihdon jälkeen käyttäjän kaikki tokenit
 * mitätöidään, myös aiemmin pyydetyt.
 */
@Service
public class PasswordResetService {

    /** Lyhyt voimassaolo rajaa ajan, jonka varastettu tai unohtunut linkki on hyödynnettävissä. */
    public static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

    /**
     * Uutta linkkiä ei lähetetä samalle käyttäjälle tätä useammin. Ilman rajaa kuka tahansa voisi
     * täyttää toisen käyttäjän sähköpostilaatikon palautuslomakkeella.
     */
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);

    private final SecureRandom random = new SecureRandom();
    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, AppUserRepository userRepository,
                                UserService userService, ApplicationEventPublisher events) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.events = events;
        this.clock = Clock.systemUTC();
    }

    /**
     * Käynnistää palautuksen. Metodi ei kerro kutsujalle, löytyikö osoitetta: käyttöliittymä näyttää
     * aina saman viestin, jottei lomakkeella voi selvittää, kenellä on tunnus palvelussa
     * (user enumeration). Samasta syystä myös uudelleenlähetyksen rajoitus ({@link #RESEND_INTERVAL})
     * ohittaa pyynnön hiljaa.
     */
    @Transactional
    public void requestReset(String email) {
        Instant now = clock.instant();
        tokenRepository.deleteExpired(now);
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(AppUser::isEnabled)
                .filter(user -> !recentlyRequested(user, now))
                .ifPresent(user -> {
                    // 32 satunnaista tavua = 256 bittiä entropiaa; Base64URL, jotta token kelpaa
                    // sellaisenaan URL-parametriksi. UUID olisi heikompi (122 bittiä, osin ennustettava rakenne).
                    byte[] bytes = new byte[32];
                    random.nextBytes(bytes);
                    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

                    tokenRepository.save(new PasswordResetToken(sha256(rawToken), user, now.plus(TOKEN_VALIDITY)));
                    // Viesti lähtee tapahtuman kautta vasta commitin jälkeen (ks. MailService).
                    events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getFullName(),
                            rawToken, TOKEN_VALIDITY.toMinutes()));
                });
    }

    /** Token, joka vanhenee myöhemmin kuin (nyt + voimassaolo - väli), on luotu viimeisen välin aikana. */
    private boolean recentlyRequested(AppUser user, Instant now) {
        return tokenRepository.existsByUserIdAndUsedAtIsNullAndExpiresAtAfter(user.getId(),
                now.plus(TOKEN_VALIDITY).minus(RESEND_INTERVAL));
    }

    @Transactional(readOnly = true)
    public boolean isValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return tokenRepository.findByTokenHash(sha256(rawToken))
                .map(t -> t.isUsable(clock.instant()))
                .orElse(false);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException("Linkki on vanhentunut tai jo käytetty. Pyydä uusi linkki."));
        userService.changePassword(token.getUser(), newPassword);
        tokenRepository.invalidateAllForUser(token.getUser(), now);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 on jokaisen Java-toteutuksen pakollinen algoritmi, joten tänne ei päädytä.
            throw new IllegalStateException(e);
        }
    }
}
