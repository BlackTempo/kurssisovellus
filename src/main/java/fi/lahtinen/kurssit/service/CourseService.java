package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.repository.CourseFilter;
import fi.lahtinen.kurssit.repository.CourseRepository;
import fi.lahtinen.kurssit.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static fi.lahtinen.kurssit.service.DataChangedEvent.Action;
import static fi.lahtinen.kurssit.service.DataChangedEvent.EntityType;

/** Kurssien liiketoimintalogiikka (vaatimus 3: CRUD, sekä Criteria-haun palvelurajapinta). */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher events;

    public CourseService(CourseRepository courseRepository, FileStorageService fileStorageService,
                         ApplicationEventPublisher events) {
        this.courseRepository = courseRepository;
        this.fileStorageService = fileStorageService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Course> findAllWithDetails() {
        return courseRepository.findAllWithDetails();
    }

    @Transactional(readOnly = true)
    public List<Course> findAllForSelection() {
        return courseRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Course> findWithDetails(Long id) {
        return courseRepository.findWithDetailsById(id);
    }

    @Transactional(readOnly = true)
    public List<Course> findUpcoming(int max) {
        return courseRepository.findUpcoming(LocalDate.now(), Limit.of(max));
    }

    @Transactional(readOnly = true)
    public long count() {
        return courseRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Course> search(CourseFilter filter, int offset, int limit) {
        return courseRepository.search(filter, offset, limit);
    }

    @Transactional(readOnly = true)
    public long countSearch(CourseFilter filter) {
        return courseRepository.count(filter);
    }

    @Transactional
    public Course save(Course course) {
        boolean duplicate = course.isNew()
                ? courseRepository.existsByCode(course.getCode())
                : courseRepository.existsByCodeAndIdNot(course.getCode(), course.getId());
        if (duplicate) {
            throw new BusinessException("Kurssikoodi " + course.getCode() + " on jo käytössä");
        }
        // Puhdistus tehdään palvelussa eikä näkymässä, jotta mikään tallennuspolku (lomake, CSV,
        // tuleva REST-rajapinta) ei voi ohittaa sitä.
        course.setDescription(HtmlSanitizer.clean(course.getDescription()));
        Action action = course.isNew() ? Action.CREATED : Action.UPDATED;
        Course saved = courseRepository.save(course);
        publish(action, saved);
        return saved;
    }

    @Transactional
    public void delete(Course course) {
        Course managed = courseRepository.findWithDetailsById(course.getId())
                .orElseThrow(() -> new BusinessException("Kurssia ei löydy – se on ehkä jo poistettu"));
        // Student omistaa M:N-liitostaulun, joten liitokset on purettava opiskelijoiden puolelta.
        // Pelkkä kurssin poisto rikkoisi vierasavainrajoitteen course_student-taulussa.
        for (Student student : managed.getStudents()) {
            student.getCourses().remove(managed);
        }
        fileStorageService.deleteAllForCourse(managed.getId());
        courseRepository.delete(managed);
        publish(Action.DELETED, managed);
    }

    private void publish(Action action, Course course) {
        events.publishEvent(new DataChangedEvent(EntityType.COURSE, action, course.getCode() + " " + course.getName(),
                SecurityUtils.currentUsernameOrSystem(), Instant.now()));
    }
}
