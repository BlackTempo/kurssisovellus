package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;

/**
 * Työhuone – toinen entiteetti, 1:1-relaatio opettajaan (vaatimus 2).
 * <p>
 * Office on relaation <em>omistava</em> puoli: vierasavain {@code teacher_id} on tässä taulussa ja
 * {@code unique = true} pakottaa tietokantatasolla, että yhdellä opettajalla on enintään yksi huone.
 * Pelkkä JPA:n {@code @OneToOne} ei yksinään estä duplikaatteja tietokannassa.
 * <p>
 * Validoinnit (vaatimus 5): @NotBlank, @Pattern, @Min/@Max, @Positive, @Size, @NotNull.
 */
@Entity
@Audited
public class Office extends AbstractAuditableEntity {

    @NotBlank(message = "Huonenumero on pakollinen")
    @Pattern(regexp = "^[A-Z][0-9]{3}$", message = "Huonenumeron muoto on kirjain + 3 numeroa, esim. A123")
    private String roomNumber;

    @NotBlank(message = "Rakennus on pakollinen")
    @Size(max = 60, message = "Rakennuksen nimi saa olla enintään 60 merkkiä")
    private String building;

    @NotNull(message = "Kerros on pakollinen")
    @Min(value = -2, message = "Kerros voi olla vähintään -2")
    @Max(value = 20, message = "Kerros voi olla enintään 20")
    private Integer floor;

    @Pattern(regexp = "^$|^[0-9]{3,5}$", message = "Alanumero on 3–5 numeroa")
    private String phoneExtension;

    @NotNull(message = "Kapasiteetti on pakollinen")
    @Positive(message = "Kapasiteetin on oltava positiivinen")
    @Max(value = 50, message = "Kapasiteetti voi olla enintään 50")
    private Integer capacity;

    @NotNull(message = "Opettaja on valittava")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", unique = true)
    private Teacher teacher;

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public String getPhoneExtension() {
        return phoneExtension;
    }

    public void setPhoneExtension(String phoneExtension) {
        this.phoneExtension = phoneExtension;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    @Override
    public String toString() {
        return roomNumber + " (" + building + ")";
    }
}
