package com.lightflare.server.application;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationEdgeRepository extends CrudRepository<ApplicationEdge, String> {

    @Query("""
            SELECT *
            FROM application_edge
            WHERE application_version_id = :applicationVersionId
            ORDER BY id ASC
            """)
    List<ApplicationEdge> findByApplicationVersionId(@Param("applicationVersionId") String applicationVersionId);

    @Modifying
    @Query("""
            DELETE FROM application_edge
            WHERE application_version_id IN (
                SELECT id
                FROM application_version
                WHERE application_id = :applicationId
            )
            """)
    int deleteByApplicationId(@Param("applicationId") String applicationId);
}
