package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criteria API -haun testit (Suodattaminen, kohdat 1–5). Käyttää DataInitializerin demodataa,
 * joten odotetut tulokset on johdettu siitä.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseSearchTest {

    @Autowired
    CourseRepository courseRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    TeacherRepository teacherRepository;

    private List<String> codes(CourseFilter filter) {
        List<Course> result = courseRepository.search(filter, 0, 100);
        assertThat(courseRepository.count(filter)).as("count vastaa hakutulosta").isEqualTo(result.size());
        return result.stream().map(Course::getCode).toList();
    }

    private static CourseFilter filter() {
        return CourseFilter.empty();
    }

    @Test
    void emptyFilterReturnsAllCourses() {
        assertThat(codes(filter())).hasSize((int) courseRepository.count());
    }

    @Test
    void partialTextSearchIsCaseInsensitive() {
        CourseFilter f = new CourseFilter("jav", null, null, null, null, null, null, null, null);
        assertThat(codes(f)).containsExactly("JAV-101");
    }

    @Test
    void multipleInputFieldsAreCombinedWithAnd() {
        CourseFilter f = new CourseFilter(null, "perusteet", 5, null, null, null, null, null, null);
        assertThat(codes(f)).containsExactlyInAnyOrder("JAV-101", "TKA-150", "TIE-330");
        CourseFilter g = new CourseFilter(null, "perusteet", 10, null, null, null, null, null, null);
        assertThat(codes(g)).isEmpty();
    }

    @Test
    void dateRangeFilter() {
        LocalDate today = LocalDate.now();
        CourseFilter past = new CourseFilter(null, null, null, null, today, null, null, null, null);
        assertThat(codes(past)).containsExactlyInAnyOrder("JAV-101", "TKA-150");
        CourseFilter range = new CourseFilter(null, null, null, today.plusDays(1), today.plusWeeks(6), null, null, null, null);
        assertThat(codes(range)).containsExactlyInAnyOrder("WEB-201", "UXD-110", "ALG-220");
    }

    @Test
    void joinToStudentManyToMany() {
        Student vaino = studentRepository.findByStudentNumber("S100008").orElseThrow();
        CourseFilter f = new CourseFilter(null, null, null, null, null, vaino, null, null, null);
        assertThat(codes(f)).containsExactlyInAnyOrder("JAV-101", "TKA-150", "WEB-201", "ALG-220");
    }

    @Test
    void joinFilterByTeacherLastName() {
        CourseFilter f = new CourseFilter(null, null, null, null, null, null, "virta", null, null);
        assertThat(codes(f)).containsExactlyInAnyOrder("JAV-101", "WEB-201");
    }

    @Test
    void orConditionCombinedWithAnd() {
        Teacher korhonen = teacherRepository.findAll().stream()
                .filter(t -> t.getLastName().equals("Korhonen")).findFirst().orElseThrow();
        // "sql" löytyy vain TKA-150:n kuvauksesta, "algoritmit" vain ALG-220:n nimestä
        CourseFilter byDescription = new CourseFilter(null, null, null, null, null, null, null, "sql", korhonen);
        assertThat(codes(byDescription)).containsExactly("TKA-150");
        CourseFilter byName = new CourseFilter(null, null, null, null, null, null, null, "algoritmit", korhonen);
        assertThat(codes(byName)).containsExactly("ALG-220");

        Teacher virtanen = teacherRepository.findAll().stream()
                .filter(t -> t.getLastName().equals("Virtanen")).findFirst().orElseThrow();
        CourseFilter wrongTeacher = new CourseFilter(null, null, null, null, null, null, null, "sql", virtanen);
        assertThat(codes(wrongTeacher)).isEmpty();
    }

    @Test
    void likeWildcardsInInputAreEscaped() {
        CourseFilter f = new CourseFilter("%", null, null, null, null, null, null, null, null);
        assertThat(codes(f)).isEmpty();
    }

    @Test
    void pagingReturnsRequestedSlice() {
        List<Course> page = courseRepository.search(filter(), 2, 2);
        assertThat(page).hasSize(2);
    }
}
