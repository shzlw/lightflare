package com.lightflare.server.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationVersionRepository extends CrudRepository<ApplicationVersion, String> {

    @Modifying
    @Query("""
            INSERT INTO application_version (
                id,
                application_id,
                version_number,
                status,
                created_at
            )
            VALUES (
                :id,
                :applicationId,
                :versionNumber,
                :status,
                :createdAt
            )
            """)
    int insertVersion(@Param("id") String id,
                      @Param("applicationId") String applicationId,
                      @Param("versionNumber") int versionNumber,
                      @Param("status") String status,
                      @Param("createdAt") OffsetDateTime createdAt);

    @Query("""
            SELECT *
            FROM application_version
            WHERE application_id = :applicationId
            ORDER BY version_number DESC, created_at DESC, id DESC
            """)
    List<ApplicationVersion> findByApplicationId(@Param("applicationId") String applicationId);

    @Query("""
            SELECT *
            FROM application_version
            WHERE application_id = :applicationId
            ORDER BY version_number DESC, created_at DESC, id DESC
            LIMIT 1
            """)
    Optional<ApplicationVersion> findLatestByApplicationId(@Param("applicationId") String applicationId);

    @Modifying
    @Query("""
            DELETE FROM application_version
            WHERE application_id = :applicationId
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
