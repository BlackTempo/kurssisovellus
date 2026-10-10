package fi.lahtinen.kurssit.config;

import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Office;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.repository.AppUserRepository;
import fi.lahtinen.kurssit.repository.CourseRepository;
import fi.lahtinen.kurssit.repository.OfficeRepository;
import fi.lahtinen.kurssit.repository.StudentRepository;
import fi.lahtinen.kurssit.repository.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Luo käynnistyksessä vaatimuksen 16 mukaiset käyttäjät sekä demodatan.
 * <p>
 * Ajetaan vain, jos tietokanta on tyhjä: tiedostopohjainen H2 ja PostgreSQL säilyttävät datan, joten
 * jokaisella käynnistyksellä ajettava alustus monistaisi rivit tai kaatuisi unique-rajoitteisiin.
 * Salasanat tiivistetään samalla PasswordEncoderilla kuin rekisteröitymisessä – tietokantaan ei
 * päädy selväkielistä salasanaa edes demotunnuksille.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final OfficeRepository officeRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository userRepository, TeacherRepository teacherRepository,
                           OfficeRepository officeRepository, CourseRepository courseRepository,
                           StudentRepository studentRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.officeRepository = officeRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            createUser("admin", "Ylläpitäjä Admin", "admin@kurssisovellus.local", "admin123", EnumSet.of(Role.ADMIN));
            createUser("user", "Uolevi Käyttäjä", "user@kurssisovellus.local", "user123", EnumSet.of(Role.USER));
            createUser("super", "Sanna Superkäyttäjä", "super@kurssisovellus.local", "super123", EnumSet.of(Role.SUPER));
            log.info("Luotiin demokäyttäjät admin/admin123, user/user123 ja super/super123");
        }
        if (teacherRepository.count() == 0) {
            createDemoData();
            log.info("Luotiin demodata (opettajat, työhuoneet, kurssit, opiskelijat)");
        }
    }

    private void createUser(String username, String fullName, String email, String password, Set<Role> roles) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(roles);
        userRepository.save(user);
    }

    private void createDemoData() {
        Teacher virtanen = teacher("Matti", "Virtanen", "matti.virtanen@koulu.fi", "+358 40 123 4567", "Lehtori", 2015);
        Teacher korhonen = teacher("Liisa", "Korhonen", "liisa.korhonen@koulu.fi", "+358 50 765 4321", "Yliopettaja", 2010);
        Teacher nieminen = teacher("Juha", "Nieminen", "juha.nieminen@koulu.fi", "040 222 3333", "Tuntiopettaja", 2021);
        Teacher makinen = teacher("Anna", "Mäkinen", "anna.makinen@koulu.fi", null, "Lehtori", 2018);

        office("A123", "Päärakennus", 1, "1123", 2, virtanen);
        office("B204", "Tekniikkatalo", 2, "2204", 1, korhonen);
        office("C310", "Kirjasto", 3, null, 4, makinen);

        LocalDate today = LocalDate.now();
        Course java = course("JAV-101", "Java-ohjelmoinnin perusteet", 5, today.minusMonths(1), today.plusMonths(2), 40, virtanen,
                "<p>Johdatus <strong>Java-ohjelmointiin</strong>: muuttujat, ehtolauseet, silmukat ja oliot.</p>");
        Course web = course("WEB-201", "Web-sovelluskehitys Vaadinilla", 5, today.plusWeeks(2), today.plusMonths(4), 30, virtanen,
                "<p>Rakennetaan moderni web-sovellus <em>Vaadin</em>- ja <em>Spring Boot</em> -kehyksillä.</p>");
        Course db = course("TKA-150", "Tietokantojen perusteet", 5, today.minusWeeks(3), today.plusMonths(1), 50, korhonen,
                "<p>Relaatiomalli, SQL ja normalisointi. Harjoituksissa käytetään PostgreSQL:ää.</p>");
        Course algo = course("ALG-220", "Algoritmit ja tietorakenteet", 10, today.plusMonths(1), today.plusMonths(5), 35, korhonen,
                "<p>Lajittelu, haku, puut ja verkot sekä aikavaativuusanalyysi.</p>");
        Course ux = course("UXD-110", "Käyttöliittymäsuunnittelu", 3, today.plusWeeks(5), today.plusMonths(3), 25, nieminen,
                "<p>Käytettävyys, saavutettavuus ja prototypointi Figmalla.</p>");
        Course sec = course("TIE-330", "Tietoturvan perusteet", 5, today.plusMonths(2), today.plusMonths(4), 30, makinen,
                "<p>OWASP Top 10, salaus ja turvallinen ohjelmistokehitys.</p>");

        student("S100001", "Aino", "Laine", "aino.laine@opiskelija.fi", 2002, Set.of(java, db, ux));
        student("S100002", "Eetu", "Heikkinen", "eetu.heikkinen@opiskelija.fi", 2001, Set.of(java, web));
        student("S100003", "Helmi", "Koskinen", "helmi.koskinen@opiskelija.fi", 2003, Set.of(db, algo));
        student("S100004", "Onni", "Järvinen", "onni.jarvinen@opiskelija.fi", 2000, Set.of(web, sec, algo));
        student("S100005", "Venla", "Lehtonen", "venla.lehtonen@opiskelija.fi", 2004, Set.of(java));
        student("S100006", "Leo", "Saarinen", "leo.saarinen@opiskelija.fi", 1999, Set.of(ux, sec));
        student("S100007", "Ella", "Salminen", "ella.salminen@opiskelija.fi", 2002, Set.of());
        student("S100008", "Väinö", "Heinonen", "vaino.heinonen@opiskelija.fi", 2001, Set.of(java, db, web, algo));
    }

    private Teacher teacher(String first, String last, String email, String phone, String title, int hireYear) {
        Teacher t = new Teacher();
        t.setFirstName(first);
        t.setLastName(last);
        t.setEmail(email);
        t.setPhone(phone);
        t.setTitle(title);
        t.setHireDate(LocalDate.of(hireYear, 8, 1));
        return teacherRepository.save(t);
    }

    private void office(String room, String building, int floor, String ext, int capacity, Teacher teacher) {
        Office o = new Office();
        o.setRoomNumber(room);
        o.setBuilding(building);
        o.setFloor(floor);
        o.setPhoneExtension(ext);
        o.setCapacity(capacity);
        o.setTeacher(teacher);
        officeRepository.save(o);
    }

    private Course course(String code, String name, int credits, LocalDate start, LocalDate end, int max,
                          Teacher teacher, String description) {
        Course c = new Course();
        c.setCode(code);
        c.setName(name);
        c.setCredits(credits);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setMaxStudents(max);
        c.setTeacher(teacher);
        c.setDescription(description);
        return courseRepository.save(c);
    }

    private void student(String number, String first, String last, String email, int birthYear, Set<Course> courses) {
        Student s = new Student();
        s.setStudentNumber(number);
        s.setFirstName(first);
        s.setLastName(last);
        s.setEmail(email);
        s.setBirthDate(LocalDate.of(birthYear, 3, 15));
        // Aloituspäivä ei saa olla tulevaisuudessa (@PastOrPresent), joten rajataan se tähän päivään.
        LocalDate enrolled = LocalDate.of(birthYear + 20, 9, 1);
        s.setEnrolledAt(enrolled.isAfter(LocalDate.now()) ? LocalDate.now() : enrolled);
        s.getCourses().addAll(courses);
        studentRepository.save(s);
    }
}
