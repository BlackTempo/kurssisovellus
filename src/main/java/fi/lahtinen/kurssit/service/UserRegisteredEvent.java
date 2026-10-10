package fi.lahtinen.kurssit.service;

/**
 * Julkaistaan, kun uusi käyttäjä on rekisteröitynyt (vaatimus 26). Sisältää vain tarvittavat
 * merkkijonot eikä entiteettiä, koska kuuntelija ajetaan eri säikeessä transaktion päätyttyä,
 * jolloin entiteetti olisi irrallinen (detached).
 */
public record UserRegisteredEvent(String username, String fullName, String email) {
}
