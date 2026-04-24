package com.lightflare.server.application;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationStepRepository extends CrudRepository<ApplicationStep, String> {

    @Query("""
            SELECT *
            FROM application_step
            WHERE application_version_id = :applicationVersionId
            ORDER BY step_key ASC, id ASC
            """)
    List<ApplicationStep> findByApplicationVersionId(@Param("applicationVersionId") String applicationVersionId);

    @Modifying
    @Query("""
            DELETE FROM application_step
            WHERE application_version_id IN (
                SELECT id
                FROM application_version
                WHERE application_id = :applicationId
            )
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
