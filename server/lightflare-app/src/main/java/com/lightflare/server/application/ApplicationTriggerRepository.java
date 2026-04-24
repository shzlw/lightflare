package com.lightflare.server.application;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationTriggerRepository extends CrudRepository<ApplicationTrigger, String> {

    @Modifying
    @Query("""
            INSERT INTO application_trigger (
                id,
                application_version_id,
                trigger_type,
                start_step_id,
                config_json
            )
            VALUES (
                :id,
                :applicationVersionId,
                :triggerType,
                :startStepId,
                :configJson
            )
            """)
    int insertTrigger(@Param("id") String id,
                      @Param("applicationVersionId") String applicationVersionId,
                      @Param("triggerType") String triggerType,
                      @Param("startStepId") String startStepId,
                      @Param("configJson") String configJson);

    @Modifying
    @Query("""
            UPDATE application_trigger
            SET trigger_type = :triggerType,
                start_step_id = :startStepId,
                config_json = :configJson
            WHERE id = :id
            """)
    int updateTrigger(@Param("id") String id,
                      @Param("triggerType") String triggerType,
                      @Param("startStepId") String startStepId,
                      @Param("configJson") String configJson);

    @Query("""
            SELECT *
            FROM application_trigger
            WHERE application_version_id = :applicationVersionId
            ORDER BY id ASC
            """)
    List<ApplicationTrigger> findByApplicationVersionId(@Param("applicationVersionId") String applicationVersionId);

    @Query("""
            SELECT *
            FROM application_trigger
            WHERE application_version_id = :applicationVersionId
              AND trigger_type = :triggerType
            ORDER BY id ASC
            """)
    List<ApplicationTrigger> findByApplicationVersionIdAndType(@Param("applicationVersionId") String applicationVersionId,
                                                               @Param("triggerType") String triggerType);

    @Modifying
    @Query("""
            DELETE FROM application_trigger
            WHERE id = :id
            """)
    int deleteTriggerById(@Param("id") String id);

    @Modifying
    @Query("""
            DELETE FROM application_trigger
            WHERE application_version_id IN (
                SELECT id
                FROM application_version
                WHERE application_id = :applicationId
            )
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
