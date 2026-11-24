package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.KbAccessScope;
import com.helpdesk.modules.knowledge.entity.KbArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    boolean existsByArticleCode(String articleCode);

    Optional<KbArticle> findByArticleCode(String articleCode);

    Page<KbArticle> findByAccessScopeAndCurrentPublishedVersionIdIsNotNullOrderByUpdatedAtDesc(
            KbAccessScope scope, Pageable pageable
    );

    Page<KbArticle> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
