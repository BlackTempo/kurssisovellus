package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

/**
 * Enversin revisio, johon on lisätty muutoksen tekijä (vaatimukset 31–32).
 * <p>
 * Oletusrevisio tallentaa vain numeron ja aikaleiman. Historianäkymässä halutaan kuitenkin näyttää
 * <em>kuka</em> muutoksen teki, joten {@link UserRevisionListener} kirjoittaa käyttäjänimen jokaiseen
 * revisioon. Yksi revisio kattaa koko transaktion, joten esim. kurssin ja sen ilmoittautumisten
 * muutos samassa tallennuksessa näkyy samalla tekijällä ja aikaleimalla.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(UserRevisionListener.class)
public class CustomRevisionEntity extends DefaultRevisionEntity {

    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
