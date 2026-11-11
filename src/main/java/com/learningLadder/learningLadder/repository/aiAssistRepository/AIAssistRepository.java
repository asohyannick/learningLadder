package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.entity.aiAssist.AIAssist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AIAssistRepository extends JpaRepository<AIAssist, UUID>, JpaSpecificationExecutor<AIAssist> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<AIAssist> findBySlugAndActiveTrue(String slug);
}