package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Kurssi – kolmas entiteetti.
 * <ul>
 *   <li>N:1 {@link Teacher} – opettajalla on monta kurssia (vaatimus 3, 1:N).</li>
 *   <li>M:N {@link Student} – käänteinen puoli; liitostaulun omistaa Student (vaatimus 4).</li>
 * </ul>
 * Validoinnit (vaatimus 5): @NotBlank, @Pattern, @Size, @Min/@Max, @Positive, @NotNull, @AssertTrue.
 */
@Entity
@Audited
public class Course extends AbstractAuditableEntity {

    @NotBlank(message = "Kurssikoodi on pakollinen")
    @Pattern(regexp = "^[A-Z]{2,4}-[0-9]{3}$", message = "Kurssikoodin muoto on esim. JAV-101")
    @Column(unique = true, length = 10)
    private String code;

    @NotBlank(message = "Nimi on pakollinen")
    @Size(min = 3, max = 100, message = "Nimen pituus 3–100 merkkiä")
    private String name;

    /**
     * Quill-editorin tuottamaa HTML:ää. Tallennetaan sellaisenaan (sanitoituna), jotta muotoilu säilyy.
     * Raja on reilu, koska HTML-tagit kasvattavat pituutta selvästi näkyvää tekstiä enemmän.
     */
    @Size(max = 10000, message = "Kuvaus on liian pitkä")
    @Column(length = 10000)
    private String description;

    @NotNull(message = "Opintopisteet on pakollinen")
    @Min(value = 1, message = "Vähintään 1 op")
    @Max(value = 30, message = "Enintään 30 op")
    private Integer credits;

    @NotNull(message = "Alkupäivä on pakollinen")
    private LocalDate startDate;

    @NotNull(message = "Loppupäivä on pakollinen")
    private LocalDate endDate;

    @NotNull(message = "Maksimiosallistujamäärä on pakollinen")
    @Positive(message = "Osallistujamäärän on oltava positiivinen")
    @Max(value = 500, message = "Enintään 500 osallistujaa")
    private Integer maxStudents;

    @NotNull(message = "Opettaja on valittava")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Teacher teacher;

    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new HashSet<>();

    /**
     * Ristiinvalidointi kahden kentän välillä. Kenttäkohtaiset annotaatiot eivät pysty vertaamaan
     * kenttiä keskenään, joten tarkistus tehdään metodina. {@link Transient}, jotta JPA ei yritä
     * tulkita metodia sarakkeeksi. Lomake tekee saman tarkistuksen Binderissä, jotta käyttäjä näkee
     * virheen heti kentän vieressä; tämä annotaatio on varmistus palvelu- ja CSV-tasolla.
     */
    @Transient
    @AssertTrue(message = "Loppupäivä ei voi olla ennen alkupäivää")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Set<Student> getStudents() {
        return students;
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
