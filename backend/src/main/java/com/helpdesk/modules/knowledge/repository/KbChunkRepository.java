package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    /**
     * HARD INVARIANT (UC06 & UC07): AI RAG CHỈ được truy xuất các kb_chunk đang is_active = TRUE
     * thuộc kb_version đang ở trạng thái PUBLISHED. Bài bị thu hồi (ARCHIVED) lập tức bị loại bỏ.
     */
    @Query("""
            SELECT c FROM KbChunk c
            JOIN KbVersion v ON v.id = c.versionId
            WHERE c.active = true AND v.status = 'PUBLISHED'
            """)
    List<KbChunk> findAllActivePublishedChunks();

    List<KbChunk> findByVersionIdOrderByChunkIndexAsc(Long versionId);

    @Modifying
    @Query("UPDATE KbChunk c SET c.active = false WHERE c.versionId = :versionId AND c.active = true")
    int deactivateChunksByVersionId(@Param("versionId") Long versionId);
}
