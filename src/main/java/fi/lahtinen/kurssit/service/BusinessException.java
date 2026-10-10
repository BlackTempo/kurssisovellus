package fi.lahtinen.kurssit.service;

/**
 * Liiketoimintasäännön rikkominen (esim. kurssikoodi on jo käytössä).
 * <p>
 * Oma poikkeustyyppi, jotta näkymät voivat näyttää viestin käyttäjälle sellaisenaan, mutta
 * odottamattomat tekniset virheet (tietokantayhteys tms.) eivät vuoda käyttöliittymään.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
