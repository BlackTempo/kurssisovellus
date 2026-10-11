package fi.lahtinen.kurssit.service;

/**
 * Julkaistaan, kun salasanan palautustoken on luotu (vaatimus 27). {@link MailService} lähettää
 * linkin vasta commitin jälkeen, jotta sähköpostissa ei koskaan ole tokenia, jota ei tallentunut.
 *
 * @param rawToken tiivistämätön token; on vain muistissa eikä päädy tietokantaan
 */
public record PasswordResetRequestedEvent(String email, String fullName, String rawToken, long validMinutes) {

    /** Raakatoken jätetään pois, jottei se päädy lokiin, jos tapahtuma joskus lokitetaan. */
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[email=" + email + "]";
    }
}
