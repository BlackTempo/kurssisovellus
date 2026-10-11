package fi.lahtinen.kurssit.service;

import java.time.Instant;
import java.util.Locale;

/**
 * Sovelluksen sisäinen tapahtuma, joka julkaistaan kun jokin entiteetti muuttuu.
 * {@link Broadcaster} välittää sen kaikille avoimille selainikkunoille Server Pushilla (vaatimus 22).
 * <p>
 * Entiteettityyppi ja toiminto ovat enumeita eivätkä valmiita merkkijonoja, jotta lokalisoitu
 * näkymä (etusivu) voi kääntää viestin käyttäjän kielelle. {@link #describe()} on suomenkielinen
 * oletusmuoto näkymille, joita ei ole lokalisoitu.
 *
 * @param entityType muuttuneen entiteetin tyyppi
 * @param action     mitä tapahtui (lisätty/muokattu/poistettu)
 * @param label      muuttuneen rivin tunniste käyttäjälle
 * @param username   muutoksen tekijä
 * @param at         ajankohta
 */
public record DataChangedEvent(EntityType entityType, Action action, String label, String username, Instant at) {

    public enum EntityType {
        COURSE("kurssi"), TEACHER("opettaja"), OFFICE("työhuone"), STUDENT("opiskelija");

        private final String finnishName;

        EntityType(String finnishName) {
            this.finnishName = finnishName;
        }

        /** Käännösavain, esim. {@code entity.course}. */
        public String translationKey() {
            return "entity." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Action {
        CREATED("lisäsi"), UPDATED("muokkasi"), DELETED("poisti");

        private final String verb;

        Action(String verb) {
            this.verb = verb;
        }

        /** Käännösavain, esim. {@code activity.created}. */
        public String translationKey() {
            return "activity." + name().toLowerCase(Locale.ROOT);
        }
    }

    public String describe() {
        return username + " " + action.verb + ": " + entityType.finnishName + " " + label;
    }
}
