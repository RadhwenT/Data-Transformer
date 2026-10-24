package io.github.radhwent.datatransformer.repository;

import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleRepository extends JpaRepository<Rule, Long> {
    Optional<Rule> findByType(RuleType type);
}