package fi.lahtinen.kurssit.service;

import java.time.Instant;

/**
 * Sovelluksen sisäinen tapahtuma, joka julkaistaan kun jokin entiteetti muuttuu.
 * {@link Broadcaster} välittää sen kaikille avoimille selainikkunoille Server Pushilla (vaatimus 22).
 *
 * @param entityType entiteetin näyttönimi, esim. "Kurssi"
 * @param action     mitä tapahtui (lisätty/muokattu/poistettu)
 * @param label      muuttuneen rivin tunniste käyttäjälle
 * @param username   muutoksen tekijä
 * @param at         ajankohta
 */
public record DataChangedEvent(String entityType, Action action, String label, String username, Instant at) {

    public enum Action {
        CREATED("lisäsi"), UPDATED("muokkasi"), DELETED("poisti");

        private final String verb;

        Action(String verb) {
            this.verb = verb;
        }

        public String verb() {
            return verb;
        }
    }

    public String describe() {
        return username + " " + action.verb() + ": " + entityType.toLowerCase() + " " + label;
    }
}
