package com.lightflare.server.user;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserIdentityRepository extends CrudRepository<AppUserIdentity, String> {

    @Modifying
    @Query("""
            INSERT INTO app_user_identity (
                id,
                app_user_id,
                provider,
                external_user_id,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :appUserId,
                :provider,
                :externalUserId,
                :createdAt,
                :updatedAt
            )
            """)
    int insert(@Param("id") String id,
               @Param("appUserId") String appUserId,
               @Param("provider") String provider,
               @Param("externalUserId") String externalUserId,
               @Param("createdAt") OffsetDateTime createdAt,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM app_user_identity
            WHERE app_user_id = :appUserId
            ORDER BY provider ASC, external_user_id ASC, id ASC
            """)
    List<AppUserIdentity> findByAppUserId(@Param("appUserId") String appUserId);

    @Query("""
            SELECT *
            FROM app_user_identity
            WHERE LOWER(provider) = LOWER(:provider)
              AND LOWER(external_user_id) = LOWER(:externalUserId)
            LIMIT 1
            """)
    Optional<AppUserIdentity> findByProviderAndExternalUserId(@Param("provider") String provider,
                                                              @Param("externalUserId") String externalUserId);

    @Query("""
            SELECT *
            FROM app_user_identity
            WHERE id = :id
              AND app_user_id = :appUserId
            LIMIT 1
            """)
    Optional<AppUserIdentity> findByIdAndAppUserId(@Param("id") String id, @Param("appUserId") String appUserId);

    @Modifying
    @Query("""
            UPDATE app_user_identity
            SET provider = :provider,
                external_user_id = :externalUserId,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateIdentity(@Param("id") String id,
                       @Param("provider") String provider,
                       @Param("externalUserId") String externalUserId,
                       @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            DELETE FROM app_user_identity
            WHERE id = :id
            """)
    int deleteByIdValue(@Param("id") String id);

    @Modifying
    @Query("""
            DELETE FROM app_user_identity
            WHERE app_user_id = :appUserId
            """)
    int deleteByAppUserId(@Param("appUserId") String appUserId);
}
