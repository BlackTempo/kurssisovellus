package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.Hibernate;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Kaikkien domain-entiteettien yhteinen yliluokka.
 * <p>
 * Spring Data Auditing (vaatimus 30) täyttää luonti- ja muokkaustiedot automaattisesti
 * {@link AuditingEntityListener}in kautta, joten palvelukerroksen ei tarvitse muistaa asettaa niitä.
 * Auditing kertoo vain <em>viimeisimmän</em> tilan; koko muutoshistoria tulee Enversiltä (vaatimus 31),
 * minkä vuoksi myös tämä yliluokka on {@link Audited} – muuten Envers ei tallentaisi perittyjä kenttiä.
 */
@MappedSuperclass
@Audited
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistinen lukitus: jos kaksi käyttäjää muokkaa samaa riviä yhtä aikaa, jälkimmäinen tallennus
     * epäonnistuu sen sijaan, että se ylikirjoittaisi hiljaa toisen muutokset. Tämä on tärkeää, koska
     * Vaadin-lomakkeet pitävät entiteettiä muistissa pitkään ennen tallennusta.
     */
    @Version
    private Integer version;

    @CreatedBy
    @Column(updatable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;

    @LastModifiedBy
    @Column(length = 50)
    private String lastModifiedBy;

    @LastModifiedDate
    private Instant lastModifiedDate;

    public Long getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public boolean isNew() {
        return id == null;
    }

    /*
     * equals/hashCode perustuvat id:hen, koska samasta tietokantarivistä syntyy eri Java-olioita eri
     * transaktioissa (esim. MultiSelectComboBoxin valinnat vs. Student.courses). Hibernate.getClass
     * purkaa lazy-proxyt, jotta proxy ja varsinainen olio tulkitaan samaksi. Tallentamattomat oliot
     * (id == null) ovat yhtä suuria vain itsensä kanssa.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && Objects.equals(id, ((AbstractAuditableEntity) o).getId());
    }

    /*
     * Vakio luokkakohtainen hash: jos hash laskettaisiin id:stä, olio "katoaisi" HashSetistä
     * tallennuksen jälkeen, kun id muuttuu null -> arvo.
     */
    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
