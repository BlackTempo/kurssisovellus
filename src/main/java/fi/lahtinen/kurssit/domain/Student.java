package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Opiskelija – neljäs entiteetti, M:N-relaatio kursseihin (vaatimus 4).
 * <p>
 * Student omistaa liitostaulun {@code course_student}, koska ilmoittautumiset muokataan opiskelijan
 * lomakkeelta. JPA tallentaa M:N-muutokset vain omistavan puolen kokoelmasta, joten jos omistajuus
 * olisi Coursella, opiskelijalomakkeen valinnat eivät koskaan päätyisi tietokantaan.
 * <p>
 * {@link Set} eikä List: Hibernate poistaa ja lisää List-tyyppisen M:N-kokoelman kaikki rivit
 * uudelleen jokaisella muutoksella, Setillä vain muuttuneet.
 * <p>
 * Validoinnit (vaatimus 5): @Pattern, @NotBlank, @Size, @Email, @Past, @PastOrPresent, @NotNull.
 */
@Entity
@Audited
public class Student extends AbstractAuditableEntity {

    @NotBlank(message = "Opiskelijanumero on pakollinen")
    @Pattern(regexp = "^S[0-9]{6}$", message = "Opiskelijanumeron muoto on S + 6 numeroa, esim. S123456")
    @Column(unique = true, length = 7)
    private String studentNumber;

    @NotBlank(message = "Etunimi on pakollinen")
    @Size(max = 50, message = "Etunimi saa olla enintään 50 merkkiä")
    private String firstName;

    @NotBlank(message = "Sukunimi on pakollinen")
    @Size(max = 50, message = "Sukunimi saa olla enintään 50 merkkiä")
    private String lastName;

    @NotBlank(message = "Sähköposti on pakollinen")
    @Email(message = "Sähköpostiosoite ei ole kelvollinen")
    private String email;

    @NotNull(message = "Syntymäaika on pakollinen")
    @Past(message = "Syntymäajan on oltava menneisyydessä")
    private LocalDate birthDate;

    @NotNull(message = "Opintojen alkupäivä on pakollinen")
    @PastOrPresent(message = "Opintojen alkupäivä ei voi olla tulevaisuudessa")
    private LocalDate enrolledAt;

    @ManyToMany
    @JoinTable(name = "course_student",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> courses = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
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

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public LocalDate getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(LocalDate enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Set<Course> getCourses() {
        return courses;
    }

    public void setCourses(Set<Course> courses) {
        this.courses = courses;
    }

    @Override
    public String toString() {
        return getFullName() + " (" + studentNumber + ")";
    }
}
