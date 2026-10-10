package fi.lahtinen.kurssit.domain;

/**
 * Käyttäjäroolit (vaatimus 16). Tallennetaan nimellä ({@code EnumType.STRING}), ei järjestysnumerolla,
 * jotta uuden roolin lisääminen keskelle ei muuta olemassa olevien käyttäjien oikeuksia.
 * Spring Security odottaa rooleille {@code ROLE_}-etuliitettä, joka lisätään vasta
 * UserDetailsServicessä – näin @RolesAllowed-annotaatioissa voi käyttää pelkkää nimeä.
 */
public enum Role {
    ADMIN,
    SUPER,
    USER
}
