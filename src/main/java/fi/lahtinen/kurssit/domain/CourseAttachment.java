package fi.lahtinen.kurssit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.envers.Audited;

/**
 * Kurssin liitetiedoston metatiedot (vaatimus 28).
 * <p>
 * Itse tiedosto on levyllä ({@code app.upload-dir}), ei tietokannassa: suuret BLOBit hidastavat
 * varmuuskopiointia ja kasvattavat Envers-historiaa, koska jokainen revisio kopioisi koko sisällön.
 * {@link #storedName} on palvelimen generoima UUID, joten käyttäjän antama tiedostonimi
 * (esim. {@code ../../etc/passwd}) ei koskaan päädy tiedostopolkuun.
 */
@Entity
@Audited
public class CourseAttachment extends AbstractAuditableEntity {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    @NotBlank
    @Size(max = 255, message = "Tiedostonimi on liian pitkä")
    private String fileName;

    @NotBlank
    @Pattern(regexp = "^(application/pdf|image/png|image/jpeg|text/plain|text/csv|"
            + "application/vnd\\.openxmlformats-officedocument\\.[a-z.]+)$",
            message = "Tiedostotyyppi ei ole sallittu")
    private String contentType;

    @NotNull
    @Positive(message = "Tiedosto on tyhjä")
    @Max(value = MAX_SIZE_BYTES, message = "Tiedosto on liian suuri (max 10 Mt)")
    private Long sizeBytes;

    @NotBlank
    @Size(max = 64)
    private String storedName;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }
}
