package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.repository.CourseRepository;
import fi.lahtinen.kurssit.repository.OfficeRepository;
import fi.lahtinen.kurssit.repository.TeacherRepository;
import fi.lahtinen.kurssit.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static fi.lahtinen.kurssit.service.DataChangedEvent.Action;
import static fi.lahtinen.kurssit.service.DataChangedEvent.EntityType;

/** Opettajien liiketoimintalogiikka (vaatimus 1: CRUD). */
@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final OfficeRepository officeRepository;
    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher events;

    public TeacherService(TeacherRepository teacherRepository, OfficeRepository officeRepository,
                          CourseRepository courseRepository, ApplicationEventPublisher events) {
        this.teacherRepository = teacherRepository;
        this.officeRepository = officeRepository;
        this.courseRepository = courseRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Teacher> findAll() {
        return teacherRepository.findAllByOrderByLastNameAscFirstNameAsc();
    }

    @Transactional(readOnly = true)
    public long count() {
        return teacherRepository.count();
    }

    @Transactional
    public Teacher save(Teacher teacher) {
        // Tarkistetaan ennen tallennusta, jotta käyttäjä saa selkeän viestin eikä tietokannan
        // unique-rajoitteen teknistä virhettä. Unique-rajoite on silti varmistuksena kilpatilanteille.
        boolean duplicate = teacher.isNew()
                ? teacherRepository.existsByEmailIgnoreCase(teacher.getEmail())
                : teacherRepository.existsByEmailIgnoreCaseAndIdNot(teacher.getEmail(), teacher.getId());
        if (duplicate) {
            throw new BusinessException("Sähköpostiosoite on jo toisen opettajan käytössä");
        }
        Action action = teacher.isNew() ? Action.CREATED : Action.UPDATED;
        Teacher saved = teacherRepository.save(teacher);
        publish(action, saved);
        return saved;
    }

    @Transactional
    public void delete(Teacher teacher) {
        // Kurssin opettaja on pakollinen, joten opettajaa ei voi poistaa kurssien alta. Kurssien
        // automaattinen poisto (cascade) olisi liian yllättävä sivuvaikutus käyttäjälle.
        if (courseRepository.countByTeacherId(teacher.getId()) > 0) {
            throw new BusinessException("Opettajalla on kursseja. Vaihda kurssien opettaja ennen poistoa.");
        }
        // Työhuone on opettajan "osa" (1:1), joten se vapautetaan opettajan mukana.
        officeRepository.findByTeacherId(teacher.getId()).ifPresent(officeRepository::delete);
        teacherRepository.deleteById(teacher.getId());
        publish(Action.DELETED, teacher);
    }

    private void publish(Action action, Teacher teacher) {
        events.publishEvent(new DataChangedEvent(EntityType.TEACHER, action, teacher.getFullName(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
    }
}
