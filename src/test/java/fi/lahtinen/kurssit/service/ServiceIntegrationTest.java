package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import fi.lahtinen.kurssit.repository.CourseRepository;
import fi.lahtinen.kurssit.repository.StudentRepository;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Palvelukerroksen integraatiotestit oikeaa (muistinvaraista) tietokantaa vasten. Sähköpostin
 * lähetys korvataan mockilla, jotta testit eivät tarvitse SMTP-palvelinta ja viestien sisältö
 * voidaan tarkistaa.
 * <p>
 * Testit eivät ole @Transactional, koska tapahtumat (sähköposti, Envers-revisiot) syntyvät vasta
 * commitissa – testitransaktion rollback estäisi juuri sen, mitä halutaan testata.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    UserService userService;
    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    CsvService csvService;
    @Autowired
    CourseService courseService;
    @Autowired
    HistoryService historyService;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registrationStoresHashAndNotifiesAdmins() {
        userService.register("testaaja", "Testi Testaaja", "testaaja@example.com", "salasana123");

        var user = userRepository.findByUsername("testaaja").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("salasana123").startsWith("$2");
        assertThat(passwordEncoder.matches("salasana123", user.getPasswordHash())).isTrue();

        // Ilmoitus lähtee asynkronisesti commitin jälkeen, joten odotetaan hetki.
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).atLeastOnce()).send(captor.capture());
        SimpleMailMessage mail = captor.getAllValues().stream()
                .filter(m -> m.getSubject().contains("testaaja")).findFirst().orElseThrow();
        assertThat(mail.getTo()).contains("admin@kurssisovellus.local");
    }

    @Test
    void duplicateUsernameIsRejected() {
        assertThatThrownBy(() -> userService.register("admin", "X", "x@example.com", "salasana123"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void passwordResetFlow() {
        clearInvocations(mailSender);
        passwordResetService.requestReset("user@kurssisovellus.local");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(captor.capture());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(captor.getValue().getText());
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        // Tietokannassa ei ole raakatokenia
        assertThat(passwordResetService.isValid(token)).isTrue();
        assertThat(passwordResetService.isValid(token + "x")).isFalse();

        passwordResetService.resetPassword(token, "uusiSalasana1");
        var user = userRepository.findByUsername("user").orElseThrow();
        assertThat(passwordEncoder.matches("uusiSalasana1", user.getPasswordHash())).isTrue();

        // Kertakäyttöisyys
        assertThat(passwordResetService.isValid(token)).isFalse();
        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "kolmasSalasana"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownEmailDoesNotSendAnything() {
        clearInvocations(mailSender);
        passwordResetService.requestReset("tuntematon@example.com");
        verify(mailSender, org.mockito.Mockito.after(500).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void csvExportImportRoundTrip() {
        byte[] exported = csvService.exportStudents();
        String text = new String(exported, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿opiskelijanumero;etunimi");
        assertThat(text).contains("S100008;Väinö;Heinonen");

        String importCsv = "﻿opiskelijanumero;etunimi;sukunimi;sahkoposti;syntymaaika;aloituspaiva;kurssit\n"
                + "S200001;Uusi;Opiskelija;uusi@opiskelija.fi;2003-05-05;2024-09-01;JAV-101,TKA-150\n"
                + "S100001;Aino;Laine-Muokattu;aino.laine@opiskelija.fi;2002-03-15;2022-09-01;JAV-101\n"
                + "X1;Huono;Rivi;ei-sahkoposti;2003-01-01;2024-09-01;\n"
                + "S200002;Tuntematon;Kurssi;t@o.fi;2003-01-01;2024-09-01;XXX-999\n";
        CsvService.ImportResult result = csvService.importStudents(
                new ByteArrayInputStream(importCsv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).startsWith("Rivi 4");

        Student created = studentRepository.findByStudentNumber("S200001").orElseThrow();
        assertThat(created.getCourses()).extracting(Course::getCode).containsExactlyInAnyOrder("JAV-101", "TKA-150");
        assertThat(studentRepository.findByStudentNumber("S100001").orElseThrow().getLastName()).isEqualTo("Laine-Muokattu");
        assertThat(studentRepository.findByStudentNumber("X1")).isEmpty();
    }

    @Test
    @WithMockUser(username = "historiatestaaja")
    void courseChangesAreAuditedAndStoredInHistory() {
        Course course = courseService.findWithDetails(courseRepository.findByCode("UXD-110").orElseThrow().getId())
                .orElseThrow();
        course.setCredits(4);
        course.setDescription("<p>Päivitetty <script>alert(1)</script>kuvaus</p>");
        courseService.save(course);

        Course reloaded = courseRepository.findByCode("UXD-110").orElseThrow();
        // Spring Data Auditing
        assertThat(reloaded.getLastModifiedBy()).isEqualTo("historiatestaaja");
        assertThat(reloaded.getCreatedBy()).isEqualTo("system");
        // XSS-sanitointi
        assertThat(reloaded.getDescription()).doesNotContain("script").contains("Päivitetty");

        // Envers-historia
        List<HistoryService.HistoryEntry> history = historyService.forEntity(HistoryService.EntityKind.COURSE,
                reloaded.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        HistoryService.HistoryEntry latest = history.get(0);
        assertThat(latest.type()).isEqualTo(RevisionType.MOD);
        assertThat(latest.username()).isEqualTo("historiatestaaja");
        assertThat(latest.changedFields()).contains("credits", "description");
        assertThat(history.get(history.size() - 1).type()).isEqualTo(RevisionType.ADD);
    }

    @Test
    void courseCodeMustBeUnique() {
        Course existing = courseRepository.findByCode("JAV-101").orElseThrow();
        Course copy = new Course();
        copy.setCode("JAV-101");
        copy.setName("Kopio");
        copy.setCredits(5);
        copy.setMaxStudents(10);
        copy.setStartDate(existing.getStartDate());
        copy.setEndDate(existing.getEndDate());
        assertThatThrownBy(() -> courseService.save(copy)).isInstanceOf(BusinessException.class);
    }
}
