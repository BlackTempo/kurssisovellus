package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.CourseAttachment;
import fi.lahtinen.kurssit.repository.CourseAttachmentRepository;
import fi.lahtinen.kurssit.repository.CourseRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kurssiliitteiden tallennus levylle ja lataus (vaatimus 28).
 * <p>
 * Tiedostot ovat levyllä ja metatiedot tietokannassa. Docker-ympäristössä hakemisto on volume,
 * joten tiedostot säilyvät konttien uudelleenluonnin yli.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * Sallitut tyypit tarkistetaan ennen levylle kirjoittamista. Selaimen ilmoittamaan tyyppiin ei voi
     * täysin luottaa, mutta tarkistus estää ainakin tahattomat väärät tiedostot; tiedostoja ei koskaan
     * suoriteta palvelimella, ja lataus pakotetaan liitteeksi, joten väärennetty tyyppi ei ole vaarallinen.
     */
    public static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final CourseAttachmentRepository attachmentRepository;
    private final CourseRepository courseRepository;
    private final Path uploadDir;

    public FileStorageService(CourseAttachmentRepository attachmentRepository, CourseRepository courseRepository,
                              @Value("${app.upload-dir}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.courseRepository = courseRepository;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(uploadDir);
        log.info("Liitteiden tallennushakemisto: {}", uploadDir);
    }

    @Transactional(readOnly = true)
    public List<CourseAttachment> findByCourse(Long courseId) {
        return attachmentRepository.findByCourseIdOrderByFileNameAsc(courseId);
    }

    @Transactional
    public CourseAttachment store(Long courseId, String originalName, String contentType, byte[] data) {
        if (data.length == 0) {
            throw new BusinessException("Tiedosto on tyhjä");
        }
        if (data.length > CourseAttachment.MAX_SIZE_BYTES) {
            throw new BusinessException("Tiedosto on liian suuri (max 10 Mt)");
        }
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Tiedostotyyppi " + contentType + " ei ole sallittu");
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException("Kurssia ei löydy"));

        // Levylle tallennettava nimi on aina palvelimen generoima UUID: käyttäjän tiedostonimeä ei
        // käytetä polussa lainkaan, joten path traversal (../) ja nimitörmäykset ovat mahdottomia.
        String storedName = UUID.randomUUID().toString();
        Path target = uploadDir.resolve(storedName);
        try {
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Tiedoston tallennus epäonnistui", e);
        }
        // Jos tietokantatransaktio perutaan (esim. validointivirhe), levylle jäänyt tiedosto
        // poistetaan, ettei hakemistoon kerry orpoja tiedostoja.
        afterRollback(() -> deleteQuietly(target));

        CourseAttachment attachment = new CourseAttachment();
        attachment.setCourse(course);
        attachment.setFileName(sanitizeDisplayName(originalName));
        attachment.setContentType(contentType);
        attachment.setSizeBytes((long) data.length);
        attachment.setStoredName(storedName);
        return attachmentRepository.save(attachment);
    }

    /** Avaa tiedoston luettavaksi. Kutsujan vastuulla on sulkea virta (Vaadin tekee sen latauksessa). */
    public InputStream open(CourseAttachment attachment) throws IOException {
        return Files.newInputStream(resolveStored(attachment.getStoredName()));
    }

    @Transactional
    public void delete(CourseAttachment attachment) {
        attachmentRepository.deleteById(attachment.getId());
        Path file = resolveStored(attachment.getStoredName());
        // Levyltä poistetaan vasta commitin jälkeen: jos transaktio peruuntuisi, metatieto jäisi
        // tietokantaan mutta tiedosto olisi jo kadonnut.
        afterCommit(() -> deleteQuietly(file));
    }

    @Transactional
    public void deleteAllForCourse(Long courseId) {
        attachmentRepository.findByCourseIdOrderByFileNameAsc(courseId).forEach(this::delete);
    }

    private Path resolveStored(String storedName) {
        Path path = uploadDir.resolve(storedName).normalize();
        // Puolustus syvyyssuunnassa: varmistetaan, ettei polku karkaa hakemistosta, vaikka
        // tietokannan storedName olisi jostain syystä peukaloitu.
        if (!path.startsWith(uploadDir)) {
            throw new SecurityException("Virheellinen tiedostopolku");
        }
        return path;
    }

    /** Näyttönimestä poistetaan polkuosat (osa selaimista lähettää koko polun, esim. C:\\fakepath\\...). */
    private static String sanitizeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "liite";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Tiedoston {} poisto epäonnistui", path, e);
        }
    }
}
