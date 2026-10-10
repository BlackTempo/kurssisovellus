package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Opettaja – sovelluksen ensimmäinen entiteetti (vaatimus 1).
 * <p>
 * Relaatiot: 1:1 {@link Office} (vaatimus 2) ja 1:N {@link Course} (vaatimus 3).
 * Molemmat ovat tällä puolella käänteisiä ({@code mappedBy}), eli vierasavaimet ovat Office- ja
 * Course-tauluissa. Näin opettajan tallentaminen ei koskaan vahingossa muuta kurssien tai
 * työhuoneiden liitoksia.
 * <p>
 * Validoinnit (vaatimus 5): @NotBlank, @Size, @Email, @Pattern, @PastOrPresent, @NotNull.
 */
@Entity
@Audited
public class Teacher extends AbstractAuditableEntity {

    @NotBlank(message = "Etunimi on pakollinen")
    @Size(max = 50, message = "Etunimi saa olla enintään 50 merkkiä")
    private String firstName;

    @NotBlank(message = "Sukunimi on pakollinen")
    @Size(max = 50, message = "Sukunimi saa olla enintään 50 merkkiä")
    private String lastName;

    @NotBlank(message = "Sähköposti on pakollinen")
    @Email(message = "Sähköpostiosoite ei ole kelvollinen")
    @Column(unique = true)
    private String email;

    // Sallii kansainvälisen (+358...) ja kotimaisen muodon välilyönteineen; tarkempi tarkistus olisi
    // maakohtainen eikä toisi tässä lisäarvoa.
    @Pattern(regexp = "^$|^\\+?[0-9 ]{7,20}$", message = "Puhelinnumero voi sisältää vain numeroita, välilyöntejä ja alun +-merkin")
    private String phone;

    @NotNull(message = "Työsuhteen alkupäivä on pakollinen")
    @PastOrPresent(message = "Työsuhteen alkupäivä ei voi olla tulevaisuudessa")
    private LocalDate hireDate;

    @Size(max = 60, message = "Titteli saa olla enintään 60 merkkiä")
    private String title;

    @OneToOne(mappedBy = "teacher", fetch = FetchType.LAZY)
    private Office office;

    @OneToMany(mappedBy = "teacher")
    private List<Course> courses = new ArrayList<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Office getOffice() {
        return office;
    }

    public void setOffice(Office office) {
        this.office = office;
    }

    public List<Course> getCourses() {
        return courses;
    }

    @Override
    public String toString() {
        return getFullName();
    }
}
