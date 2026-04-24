package com.lightflare.server.application;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRunRepository extends CrudRepository<ApplicationRun, String> {

    @Modifying
    @Query("""
            INSERT INTO application_run (
                id,
                application_id,
                application_version_id,
                trigger_id,
                status,
                input_json,
                output_json,
                error_message,
                started_by,
                started_at,
                completed_at
            )
            VALUES (
                :id,
                :applicationId,
                :applicationVersionId,
                :triggerId,
                :status,
                :inputJson,
                :outputJson,
                :errorMessage,
                :startedBy,
                :startedAt,
                :completedAt
            )
            """)
    int insertRun(@Param("id") String id,
                  @Param("applicationId") String applicationId,
                  @Param("applicationVersionId") String applicationVersionId,
                  @Param("triggerId") String triggerId,
                  @Param("status") String status,
                  @Param("inputJson") String inputJson,
                  @Param("outputJson") String outputJson,
                  @Param("errorMessage") String errorMessage,
                  @Param("startedBy") String startedBy,
                  @Param("startedAt") OffsetDateTime startedAt,
                  @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query("""
            UPDATE application_run
            SET status = :status,
                output_json = :outputJson,
                error_message = :errorMessage,
                completed_at = :completedAt
            WHERE id = :id
            """)
    int completeRun(@Param("id") String id,
                    @Param("status") String status,
                    @Param("outputJson") String outputJson,
                    @Param("errorMessage") String errorMessage,
                    @Param("completedAt") OffsetDateTime completedAt);

    @Query("""
            SELECT *
            FROM application_run
            WHERE application_id = :applicationId
            ORDER BY started_at DESC NULLS LAST, id DESC
            LIMIT :limit
            """)
    List<ApplicationRun> findRecentByApplicationId(@Param("applicationId") String applicationId,
                                                   @Param("limit") int limit);

    @Modifying
    @Query("""
            DELETE FROM application_run
            WHERE application_id = :applicationId
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
