package fi.lahtinen.kurssit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.EnumSet;
import java.util.Set;

/**
 * Sovelluksen oma käyttäjäentiteetti (vaatimus 16).
 * <p>
 * Salasanaa ei tallenneta lainkaan, vain sen BCrypt-tiiviste ({@link #passwordHash}). BCrypt valittiin,
 * koska se on tarkoituksella hidas ja suolaa jokaisen tiivisteen erikseen: vuotanutta tietokantaa ei
 * voi murtaa sateenkaaritauluilla, ja raa'an voiman yrityksiä hidastaa työkerroin.
 * <p>
 * Taulun nimi on {@code app_user}, koska {@code user} on varattu sana PostgreSQL:ssä.
 */
@Entity
@Table(name = "app_user")
@Audited
public class AppUser extends AbstractAuditableEntity {

    @NotBlank(message = "Käyttäjätunnus on pakollinen")
    @Size(min = 3, max = 30, message = "Käyttäjätunnuksen pituus 3–30 merkkiä")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Käyttäjätunnuksessa sallitaan vain kirjaimet, numerot sekä . _ -")
    @Column(unique = true, nullable = false, length = 30)
    private String username;

    @NotBlank(message = "Nimi on pakollinen")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Sähköposti on pakollinen")
    @Email(message = "Sähköpostiosoite ei ole kelvollinen")
    @Column(unique = true, nullable = false)
    private String email;

    /** Tiivistettä ei auditoida: historiaan ei haluta edes tiivisteiden vanhoja versioita. */
    @NotAudited
    @Column(nullable = false, length = 72)
    private String passwordHash;

    @NotEmpty
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = EnumSet.of(Role.USER);

    private boolean enabled = true;

    /**
     * Profiilikuva (vaatimus 19). Kuvat ovat pieniä (max 2 Mt) ja aina sidottuja yhteen käyttäjään,
     * joten ne voi pitää tietokannassa ilman erillistä tiedostovarastoa. Ei auditoida, jotta jokainen
     * kuvan vaihto ei kopioisi megatavuja historiatauluun.
     * <p>
     * Tarkoituksella ei {@code @Lob}: PostgreSQL:ssä Hibernate mappaa @Lob byte[] -kentän
     * large object -tyyppiin (oid), jota ei voi lukea auto-commit-tilassa. Pituusrajattu sarake
     * mappautuu sen sijaan bytea/varbinary-tyypiksi, joka toimii samoin sekä H2:ssa että PostgreSQL:ssä.
     */
    @NotAudited
    @Column(length = 2_200_000)
    private byte[] profileImage;

    @NotAudited
    private String profileImageType;

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public byte[] getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(byte[] profileImage) {
        this.profileImage = profileImage;
    }

    public String getProfileImageType() {
        return profileImageType;
    }

    public void setProfileImageType(String profileImageType) {
        this.profileImageType = profileImageType;
    }
}
