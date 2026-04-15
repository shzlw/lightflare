package com.lightflare.server.contextsearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContextSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<MemoryContextSearchRow> findMemoryVectorCandidates(String embeddingVector,
                                                                   String sessionId,
                                                                   String ownerUserId,
                                                                   boolean isAdmin,
                                                                   int limit) {
        String sql = """
                SELECT id AS memory_id,
                       kind AS title,
                       source AS source,
                       scope AS scope,
                       content AS content,
                       created_at AS created_at
                FROM memory
                WHERE status = 'active'
                  AND kind <> 'document'
                  AND embedding_vector IS NOT NULL
                  AND (:isAdmin = TRUE OR owner_user_id = :ownerUserId OR scope = 'public')
                  AND (:ownerUserId IS NULL OR owner_user_id = :ownerUserId OR scope = 'public')
                  AND (:sessionId IS NULL OR scope <> 'session' OR session_id = :sessionId)
                ORDER BY embedding_vector <#> CAST(:embeddingVector AS vector)
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, scopedParams(sessionId, ownerUserId, isAdmin, limit)
                .addValue("embeddingVector", embeddingVector), this::mapMemoryRow);
    }

    public List<MemoryContextSearchRow> findMemoryTextCandidates(String query,
                                                                 String sessionId,
                                                                 String ownerUserId,
                                                                 boolean isAdmin,
                                                                 int limit) {
        String sql = """
                SELECT id AS memory_id,
                       kind AS title,
                       source AS source,
                       scope AS scope,
                       content AS content,
                       created_at AS created_at
                FROM memory
                WHERE status = 'active'
                  AND kind <> 'document'
                  AND (
                        search_vector @@ websearch_to_tsquery('simple', :query)
                        OR content ILIKE '%' || :query || '%'
                      )
                  AND (:isAdmin = TRUE OR owner_user_id = :ownerUserId OR scope = 'public')
                  AND (:ownerUserId IS NULL OR owner_user_id = :ownerUserId OR scope = 'public')
                  AND (:sessionId IS NULL OR scope <> 'session' OR session_id = :sessionId)
                ORDER BY GREATEST(
                            ts_rank_cd(search_vector, websearch_to_tsquery('simple', :query)),
                            CASE WHEN content ILIKE '%' || :query || '%' THEN 0.05 ELSE 0 END
                         ) DESC,
                         updated_at DESC,
                         created_at DESC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, scopedParams(sessionId, ownerUserId, isAdmin, limit)
                .addValue("query", query), this::mapMemoryRow);
    }

    public List<DocumentChunkContextSearchRow> findDocumentChunkVectorCandidates(String embeddingVector,
                                                                                String sessionId,
                                                                                String ownerUserId,
                                                                                boolean isAdmin,
                                                                                int limit) {
        String sql = """
                SELECT m.id AS memory_id,
                       d.id AS document_id,
                       dc.id AS document_chunk_id,
                       d.file_name AS title,
                       m.scope AS scope,
                       dc.content AS content,
                       dc.chunk_index AS chunk_index,
                       dc.created_at AS created_at
                FROM document_chunk dc
                JOIN document d ON d.id = dc.document_id
                JOIN memory m ON m.id = d.memory_id
                WHERE m.status = 'active'
                  AND dc.embedding_vector IS NOT NULL
                  AND (:isAdmin = TRUE OR m.owner_user_id = :ownerUserId OR m.scope = 'public')
                  AND (:ownerUserId IS NULL OR m.owner_user_id = :ownerUserId OR m.scope = 'public')
                  AND (:sessionId IS NULL OR m.scope <> 'session' OR m.session_id = :sessionId)
                ORDER BY dc.embedding_vector <#> CAST(:embeddingVector AS vector)
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, scopedParams(sessionId, ownerUserId, isAdmin, limit)
                .addValue("embeddingVector", embeddingVector), this::mapDocumentChunkRow);
    }

    public List<DocumentChunkContextSearchRow> findDocumentChunkTextCandidates(String query,
                                                                              String sessionId,
                                                                              String ownerUserId,
                                                                              boolean isAdmin,
                                                                              int limit) {
        String sql = """
                SELECT m.id AS memory_id,
                       d.id AS document_id,
                       dc.id AS document_chunk_id,
                       d.file_name AS title,
                       m.scope AS scope,
                       dc.content AS content,
                       dc.chunk_index AS chunk_index,
                       dc.created_at AS created_at
                FROM document_chunk dc
                JOIN document d ON d.id = dc.document_id
                JOIN memory m ON m.id = d.memory_id
                WHERE m.status = 'active'
                  AND (
                        dc.search_vector @@ websearch_to_tsquery('simple', :query)
                        OR dc.content ILIKE '%' || :query || '%'
                        OR d.file_name ILIKE '%' || :query || '%'
                      )
                  AND (:isAdmin = TRUE OR m.owner_user_id = :ownerUserId OR m.scope = 'public')
                  AND (:ownerUserId IS NULL OR m.owner_user_id = :ownerUserId OR m.scope = 'public')
                  AND (:sessionId IS NULL OR m.scope <> 'session' OR m.session_id = :sessionId)
                ORDER BY GREATEST(
                            ts_rank_cd(dc.search_vector, websearch_to_tsquery('simple', :query)),
                            CASE WHEN dc.content ILIKE '%' || :query || '%' OR d.file_name ILIKE '%' || :query || '%' THEN 0.05 ELSE 0 END
                         ) DESC,
                         dc.updated_at DESC,
                         dc.created_at DESC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, scopedParams(sessionId, ownerUserId, isAdmin, limit)
                .addValue("query", query), this::mapDocumentChunkRow);
    }

    private MapSqlParameterSource scopedParams(String sessionId, String ownerUserId, boolean isAdmin, int limit) {
        return new MapSqlParameterSource()
                .addValue("sessionId", sessionId, Types.VARCHAR)
                .addValue("ownerUserId", ownerUserId, Types.VARCHAR)
                .addValue("isAdmin", isAdmin)
                .addValue("limit", limit);
    }

    private MemoryContextSearchRow mapMemoryRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MemoryContextSearchRow(
                resultSet.getString("memory_id"),
                resultSet.getString("title"),
                resultSet.getString("source"),
                resultSet.getString("scope"),
                resultSet.getString("content"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }

    private DocumentChunkContextSearchRow mapDocumentChunkRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentChunkContextSearchRow(
                resultSet.getString("memory_id"),
                resultSet.getString("document_id"),
                resultSet.getString("document_chunk_id"),
                resultSet.getString("title"),
                resultSet.getString("scope"),
                resultSet.getString("content"),
                resultSet.getObject("chunk_index", Integer.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
