package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.repository.StudentRepository;
import fi.lahtinen.kurssit.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static fi.lahtinen.kurssit.service.DataChangedEvent.Action;
import static fi.lahtinen.kurssit.service.DataChangedEvent.EntityType;

/** Opiskelijoiden liiketoimintalogiikka (vaatimus 4: CRUD + M:N-ilmoittautumiset). */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher events;

    public StudentService(StudentRepository studentRepository, ApplicationEventPublisher events) {
        this.studentRepository = studentRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Student> findAll() {
        return studentRepository.findAllByOrderByLastNameAscFirstNameAsc();
    }

    @Transactional(readOnly = true)
    public long count() {
        return studentRepository.count();
    }

    @Transactional
    public Student save(Student student) {
        boolean duplicate = student.isNew()
                ? studentRepository.existsByStudentNumber(student.getStudentNumber())
                : studentRepository.existsByStudentNumberAndIdNot(student.getStudentNumber(), student.getId());
        if (duplicate) {
            throw new BusinessException("Opiskelijanumero " + student.getStudentNumber() + " on jo käytössä");
        }
        Action action = student.isNew() ? Action.CREATED : Action.UPDATED;
        Student saved = studentRepository.save(student);
        events.publishEvent(new DataChangedEvent(EntityType.STUDENT, action, saved.getFullName(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
        return saved;
    }

    @Transactional
    public void delete(Student student) {
        // Student omistaa liitostaulun, joten Hibernate poistaa course_student-rivit automaattisesti.
        studentRepository.deleteById(student.getId());
        events.publishEvent(new DataChangedEvent(EntityType.STUDENT, Action.DELETED, student.getFullName(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
    }
}
