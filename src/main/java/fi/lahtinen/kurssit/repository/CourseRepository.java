package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Course;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Kurssien repository (vaatimus 3). Perii myös {@link CourseRepositoryCustom}in, jossa on Criteria
 * API -haku; Spring Data yhdistää custom-toteutuksen tähän rajapintaan Impl-päätteen perusteella.
 */
public interface CourseRepository extends JpaRepository<Course, Long>, CourseRepositoryCustom {

    /*
     * JOIN FETCH sekä opettajalle että opiskelijoille: grid näyttää molemmat relaatiot (1:N ja M:N).
     * DISTINCT tarvitaan, koska opiskelijoiden fetch-join monistaa kurssirivit SQL-tasolla.
     */
    @Query("select distinct c from Course c join fetch c.teacher left join fetch c.students order by c.startDate desc")
    List<Course> findAllWithDetails();

    @Query("select c from Course c join fetch c.teacher left join fetch c.students where c.id = :id")
    Optional<Course> findWithDetailsById(Long id);

    @Query("select c from Course c join fetch c.teacher where c.startDate >= :from order by c.startDate asc")
    List<Course> findUpcoming(LocalDate from, Limit limit);

    List<Course> findAllByOrderByCodeAsc();

    Optional<Course> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByCode(String code);

    long countByTeacherId(Long teacherId);
}
