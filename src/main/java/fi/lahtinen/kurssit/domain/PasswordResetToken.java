package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * Salasanan palautustoken (vaatimus 27).
 * <p>
 * Tietokantaan tallennetaan vain tokenin SHA-256-tiiviste. Sähköpostilla lähetetty raakatoken toimii
 * kertakäyttöisenä salasanana; jos tietokanta vuotaisi, tiivisteestä ei voi muodostaa toimivaa
 * palautuslinkkiä. SHA-256 riittää (BCryptiä ei tarvita), koska token on 256-bittinen satunnaisluku
 * eikä ihmisen keksimä, joten arvaushyökkäys ei ole mahdollinen.
 * <p>
 * Ei peri AbstractAuditableEntityä eikä ole @Audited: tokenit ovat lyhytikäistä teknistä dataa, eikä
 * niiden historiasta ole hyötyä – päinvastoin, historia säilyttäisi tiivisteet tarpeettomasti.
 */
@Entity
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Asetetaan käytön yhteydessä massapäivityksellä (PasswordResetTokenRepository.invalidateAllForUser). */
    private Instant usedAt;

    protected PasswordResetToken() {
        // JPA vaatii parametrittoman konstruktorin
    }

    public PasswordResetToken(String tokenHash, AppUser user, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
