package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.repository.CourseRepository;
import fi.lahtinen.kurssit.repository.StudentRepository;
import fi.lahtinen.kurssit.security.SecurityUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Opiskelijoiden CSV-vienti ja -tuonti (vaatimus 29).
 * <p>
 * Muoto: puolipiste erottimena ja UTF-8 BOM-merkillä. Suomenkielinen Excel olettaa puolipisteen
 * (pilkku on desimaalierotin) ja tunnistaa UTF-8:n vain BOMista – ilman sitä ääkköset näkyisivät
 * rikkinäisinä. Sama tiedosto voidaan siis avata Excelissä, muokata ja tuoda takaisin.
 * <p>
 * CSV-injektio: Excel suorittaa kaavana solun, joka alkaa merkillä =, +, -, @, sarkain tai rivinvaihto.
 * Koska nimet ja sähköpostit ovat käyttäjäsyötettä, vienti lisää tällaisen arvon eteen heittomerkin
 * (OWASP:n suositus), ja tuonti poistaa sen, jotta vienti ja tuonti säilyttävät arvon ennallaan.
 */
@Service
public class CsvService {

    static final String[] HEADERS = {"opiskelijanumero", "etunimi", "sukunimi", "sahkoposti",
            "syntymaaika", "aloituspaiva", "kurssit"};

    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader(HEADERS)
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .get();

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final Validator validator;
    private final ApplicationEventPublisher events;

    public CsvService(StudentRepository studentRepository, CourseRepository courseRepository,
                      Validator validator, ApplicationEventPublisher events) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.validator = validator;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public byte[] exportStudents() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // BOM kirjoitetaan ennen CSVPrinterin luontia, koska printer kirjoittaa otsikkorivin heti.
            writer.write('\uFEFF');
            CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setDelimiter(';').setHeader(HEADERS).get());
            for (Student s : studentRepository.findAllByOrderByLastNameAscFirstNameAsc()) {
                // Kurssit yhdellä solulla pilkuin eroteltuina: M:N-relaatio säilyy tiedostossa
                // ilman erillistä liitostaulutiedostoa ja on luettavissa ihmiselle.
                String courseCodes = s.getCourses().stream().map(Course::getCode).sorted()
                        .collect(Collectors.joining(","));
                printer.printRecord(protect(s.getStudentNumber()), protect(s.getFirstName()),
                        protect(s.getLastName()), protect(s.getEmail()), s.getBirthDate(), s.getEnrolledAt(),
                        courseCodes);
            }
            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Tuo opiskelijat. Olemassa oleva opiskelija (sama opiskelijanumero) päivitetään, uusi luodaan.
     * <p>
     * Kelvolliset rivit tallennetaan ja virheelliset raportoidaan rivinumeroineen. Valinta "tallenna
     * kelvolliset" eikä "kaikki tai ei mitään" tehtiin, koska käyttäjä voi korjata raportoidut rivit
     * ja tuoda tiedoston uudelleen: jo tuodut rivit vain päivittyvät, joten tuonti on idempotentti.
     */
    @Transactional
    public ImportResult importStudents(InputStream input) {
        Map<String, Course> coursesByCode = courseRepository.findAll().stream()
                .collect(Collectors.toMap(Course::getCode, Function.identity()));
        List<String> errors = new ArrayList<>();
        Set<String> seenNumbers = new HashSet<>();
        int created = 0;
        int updated = 0;

        try (Reader reader = skipBom(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord row : parser) {
                long line = row.getRecordNumber() + 1; // +1 otsikkoriville, jotta numero vastaa Exceliä
                try {
                    Student candidate = toStudent(row, coursesByCode);
                    if (!seenNumbers.add(candidate.getStudentNumber())) {
                        errors.add("Rivi " + line + ": opiskelijanumero " + candidate.getStudentNumber()
                                + " esiintyy tiedostossa useasti");
                        continue;
                    }
                    // Validoidaan erillinen ehdokasolio samoilla entiteetin annotaatioilla kuin lomakkeessa.
                    // Olemassa olevaan (Hibernaten hallitsemaan) opiskelijaan kosketaan vasta, kun rivi on
                    // todettu kelvolliseksi – muuten Hibernate tallentaisi virheelliset arvot commitissa.
                    Set<ConstraintViolation<Student>> violations = validator.validate(candidate);
                    if (!violations.isEmpty()) {
                        errors.add("Rivi " + line + ": " + violations.stream()
                                .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining(", ")));
                        continue;
                    }
                    var existing = studentRepository.findByStudentNumber(candidate.getStudentNumber());
                    if (existing.isPresent()) {
                        copyValues(candidate, existing.get());
                        updated++;
                    } else {
                        studentRepository.save(candidate);
                        created++;
                    }
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    // IllegalArgumentException tulee myös Commons CSV:ltä, jos rivillä on liian vähän sarakkeita.
                    errors.add("Rivi " + line + ": " + e.getMessage());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new BusinessException("CSV-tiedoston lukeminen epäonnistui: " + e.getMessage());
        }

        if (created + updated > 0) {
            events.publishEvent(new DataChangedEvent(DataChangedEvent.EntityType.STUDENT, DataChangedEvent.Action.UPDATED,
                    "CSV-tuonti (" + (created + updated) + " riviä)", SecurityUtils.currentUsernameOrSystem(),
                    Instant.now()));
        }
        return new ImportResult(created, updated, errors);
    }

    private static Set<Course> resolveCourses(String codes, Map<String, Course> coursesByCode) {
        Set<Course> result = new HashSet<>();
        if (codes == null || codes.isBlank()) {
            return result;
        }
        for (String code : Arrays.stream(codes.split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList()) {
            Course course = coursesByCode.get(code);
            if (course == null) {
                throw new IllegalArgumentException("tuntematon kurssikoodi " + code);
            }
            result.add(course);
        }
        return result;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null; // @NotNull-validointi raportoi puuttuvan arvon selkeämmin kuin jäsennysvirhe
        }
        return LocalDate.parse(value);
    }

    private static Student toStudent(CSVRecord row, Map<String, Course> coursesByCode) {
        Student s = new Student();
        s.setStudentNumber(unprotect(row.get("opiskelijanumero")));
        s.setFirstName(unprotect(row.get("etunimi")));
        s.setLastName(unprotect(row.get("sukunimi")));
        s.setEmail(unprotect(row.get("sahkoposti")));
        s.setBirthDate(parseDate(row.get("syntymaaika")));
        s.setEnrolledAt(parseDate(row.get("aloituspaiva")));
        s.setCourses(resolveCourses(row.get("kurssit"), coursesByCode));
        return s;
    }

    private static void copyValues(Student from, Student to) {
        to.setFirstName(from.getFirstName());
        to.setLastName(from.getLastName());
        to.setEmail(from.getEmail());
        to.setBirthDate(from.getBirthDate());
        to.setEnrolledAt(from.getEnrolledAt());
        to.setCourses(from.getCourses());
    }

    /** Estää kaavan suorituksen Excelissä lisäämällä heittomerkin vaarallisen alkumerkin eteen. */
    static String protect(String value) {
        if (value != null && !value.isEmpty() && FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    /** {@link #protect}in käänteistoiminto: poistaa viennissä lisätyn heittomerkin. */
    static String unprotect(String value) {
        if (value != null && value.length() > 1 && value.charAt(0) == '\''
                && FORMULA_PREFIXES.indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    /** Ohittaa UTF-8 BOMin, jotta ensimmäinen otsikko ei saa näkymätöntä etumerkkiä (Excel lisää BOMin). */
    private static Reader skipBom(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();
        if (first != -1 && first != '\uFEFF') {
            pushback.unread(first);
        }
        return pushback;
    }

    public record ImportResult(int created, int updated, List<String> errors) {
    }
}
