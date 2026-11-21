package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.KbVersion;
import com.helpdesk.modules.knowledge.entity.KbVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbVersionRepository extends JpaRepository<KbVersion, Long> {

    List<KbVersion> findByArticleIdOrderByVersionNumberDesc(Long articleId);

    Optional<KbVersion> findFirstByArticleIdOrderByVersionNumberDesc(Long articleId);

    Optional<KbVersion> findByArticleIdAndStatus(Long articleId, KbVersionStatus status);
}
