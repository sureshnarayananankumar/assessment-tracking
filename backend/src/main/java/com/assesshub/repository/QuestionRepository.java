package com.assesshub.repository;

import com.assesshub.domain.Question;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * @param viewerId null for administrators (see everything); otherwise own + shared questions.
     */
    @Query("""
            select q from Question q
            where q.archived = false
              and (:viewerId is null or q.owner.id = :viewerId or q.visibility = com.assesshub.domain.enums.QuestionVisibility.SHARED)
              and (:type is null or q.type = :type)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:skill is null or lower(q.skillCategory) = :skill)
              and (:search is null or lower(q.title) like :search or lower(q.tags) like :search)
            """)
    Page<Question> search(@Param("viewerId") Long viewerId,
                          @Param("type") QuestionType type,
                          @Param("difficulty") Difficulty difficulty,
                          @Param("skill") String skillLower,
                          @Param("search") String searchPattern,
                          Pageable pageable);

    @Query("select distinct q.skillCategory from Question q where q.skillCategory is not null and q.archived = false order by q.skillCategory")
    List<String> findSkillCategories();
}
