package fi.lahtinen.kurssit.service;

/**
 * Julkaistaan, kun ylläpitäjä muuttaa käyttäjän rooleja tai tilaa tai poistaa tunnuksen.
 * {@link AccessRevocationBroadcaster} kirjaa käyttäjän avoimet istunnot ulos, koska Spring Security
 * lukee roolit vain kirjautuessa eikä muutos muuten tulisi voimaan ennen uutta kirjautumista.
 */
public record UserAccessChangedEvent(String username) {
}
