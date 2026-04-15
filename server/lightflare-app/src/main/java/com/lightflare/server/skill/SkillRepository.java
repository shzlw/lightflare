package com.lightflare.server.skill;

import com.lightflare.server.skill.Skill;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillRepository extends CrudRepository<Skill, String> {

    @Modifying
    @Query("""
            INSERT INTO skill (
                id,
                name,
                description,
                visibility,
                user_id,
                source,
                content,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :name,
                :description,
                :visibility,
                :userId,
                :source,
                :content,
                :createdAt,
                :updatedAt
            )
            """)
    int insertSkill(@Param("id") String id,
                    @Param("name") String name,
                    @Param("description") String description,
                    @Param("visibility") String visibility,
                    @Param("userId") String userId,
                    @Param("source") String source,
                    @Param("content") String content,
                    @Param("createdAt") java.time.OffsetDateTime createdAt,
                    @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE skill
            SET name = :name,
                description = :description,
                visibility = :visibility,
                user_id = :userId,
                source = :source,
                content = :content,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateSkill(@Param("id") String id,
                    @Param("name") String name,
                    @Param("description") String description,
                    @Param("visibility") String visibility,
                    @Param("userId") String userId,
                    @Param("source") String source,
                    @Param("content") String content,
                    @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM skill
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Skill> findPage(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM skill
            """)
    long countSkills();

    @Query("""
            SELECT *
            FROM skill
            ORDER BY embedding_vector <#> CAST(:embeddingVector AS vector)
            LIMIT 10
            """)
    List<Skill> findByCosineSimilarity(@Param("embeddingVector") String embeddingVector);

    @Modifying
    @Query("""
            DELETE FROM skill
            WHERE id = :id
            """)
    int deleteSkillById(@Param("id") String id);
}
