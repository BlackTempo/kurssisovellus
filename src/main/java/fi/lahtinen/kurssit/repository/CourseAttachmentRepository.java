package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.CourseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Kurssiliitteiden metatiedot (vaatimus 28). */
public interface CourseAttachmentRepository extends JpaRepository<CourseAttachment, Long> {

    List<CourseAttachment> findByCourseIdOrderByFileNameAsc(Long courseId);
}
