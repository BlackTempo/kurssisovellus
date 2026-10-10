package fi.lahtinen.kurssit.domain;

import fi.lahtinen.kurssit.security.SecurityUtils;
import org.hibernate.envers.RevisionListener;

/**
 * Täyttää Envers-revisioon muutoksen tekijän.
 * <p>
 * Envers luo tämän luokan itse (ei Spring-bean), joten käyttäjä haetaan staattisesti
 * SecurityContextista. Se toimii, koska Envers kutsuu listeneriä samassa säikeessä, jossa
 * tallennuksen tehnyt pyyntö käsitellään.
 */
public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((CustomRevisionEntity) revisionEntity).setUsername(SecurityUtils.currentUsernameOrSystem());
    }
}
