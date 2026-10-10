package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Sähköpostien lähetys (vaatimukset 26 ja 27).
 * <p>
 * Kehityksessä ja Dockerissa viestit menevät Mailpit-testipalvelimeen, jonka web-käyttöliittymästä
 * (http://localhost:8025) ne voi tarkistaa. Oikeaan SMTP-palvelimeen vaihdetaan pelkillä
 * {@code spring.mail.*}-asetuksilla, koodiin ei tarvitse koskea.
 * <p>
 * Kaikki lähetykset ovat {@link Async}: SMTP-yhteys voi kestää sekunteja, eikä käyttäjän pidä odottaa
 * sitä. Virheet lokitetaan eikä niitä heitetä eteenpäin, koska kutsuja ei taustasäikeessä enää
 * pystyisi reagoimaan niihin.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final AppUserRepository userRepository;
    private final String from;
    private final String baseUrl;

    public MailService(JavaMailSender mailSender, AppUserRepository userRepository,
                       @Value("${app.mail.from}") String from, @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    /**
     * Ilmoitus ylläpitäjille uudesta käyttäjästä (vaatimus 26).
     * <p>
     * {@link TransactionalEventListener} AFTER_COMMIT: viesti lähtee vain, jos käyttäjä todella
     * tallentui. Tavallinen @EventListener lähettäisi viestin myös, jos rekisteröinti peruuntuisi
     * myöhemmin samassa transaktiossa. Koska alkuperäinen transaktio on jo päättynyt, ylläpitäjien
     * haku tarvitsee oman (REQUIRES_NEW) transaktionsa.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        List<String> adminEmails = userRepository.findByRolesContaining(Role.ADMIN).stream()
                .filter(AppUser::isEnabled)
                .map(AppUser::getEmail)
                .toList();
        if (adminEmails.isEmpty()) {
            log.warn("Uusi käyttäjä {} rekisteröityi, mutta ylläpitäjiä ei löytynyt ilmoitusta varten", event.username());
            return;
        }
        send(adminEmails.toArray(String[]::new),
                "Uusi käyttäjä rekisteröityi: " + event.username(),
                """
                Hei,

                Kurssisovellukseen on rekisteröitynyt uusi käyttäjä:

                  Käyttäjätunnus: %s
                  Nimi:           %s
                  Sähköposti:     %s

                Käyttäjällä on oletuksena USER-rooli. Voit muuttaa rooleja käyttäjähallinnassa:
                %s/admin/users

                -- Kurssisovellus
                """.formatted(event.username(), event.fullName(), event.email(), baseUrl));
    }

    /** Salasanan palautuslinkki (vaatimus 27). */
    @Async
    public void sendPasswordResetLink(String email, String fullName, String rawToken, long validMinutes) {
        String link = baseUrl + "/reset-password?token=" + rawToken;
        send(new String[]{email},
                "Salasanan vaihto – Kurssisovellus",
                """
                Hei %s,

                Saimme pyynnön vaihtaa tunnuksesi salasanan. Voit asettaa uuden salasanan alla olevasta
                linkistä. Linkki on voimassa %d minuuttia ja toimii vain kerran.

                %s

                Jos et pyytänyt salasanan vaihtoa, voit jättää tämän viestin huomiotta – salasanasi
                pysyy ennallaan.

                -- Kurssisovellus
                """.formatted(fullName, validMinutes, link));
    }

    private void send(String[] to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            log.info("Sähköposti lähetetty: '{}' -> {}", subject, String.join(", ", to));
        } catch (MailException e) {
            log.error("Sähköpostin lähetys epäonnistui: '{}'", subject, e);
        }
    }
}
