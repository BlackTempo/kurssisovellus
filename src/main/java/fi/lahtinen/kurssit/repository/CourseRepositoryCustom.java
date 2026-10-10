package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Course;

import java.util.List;

/** Criteria API -pohjainen dynaaminen haku (Suodattaminen, kohdat 1–5). */
public interface CourseRepositoryCustom {

    List<Course> search(CourseFilter filter, int offset, int limit);

    long count(CourseFilter filter);
}
