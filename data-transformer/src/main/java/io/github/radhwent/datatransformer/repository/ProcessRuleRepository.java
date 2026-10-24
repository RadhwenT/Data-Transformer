package io.github.radhwent.datatransformer.repository;

import io.github.radhwent.datatransformer.entity.ProcessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessRuleRepository extends JpaRepository<ProcessRule, Long> {

    /**
     * JOIN FETCH sur "rule" : sans ca, chaque ProcessRule declenche une
     * requete separee pour charger sa Rule associee (fetch = LAZY) des que
     * processRule.getRule() est appele, un par regle du traitement (N+1).
     */
    @Query("select pr from ProcessRule pr join fetch pr.rule where pr.process.id = :processId order by pr.executionOrder asc")
    List<ProcessRule> findByProcessIdOrderByExecutionOrderAsc(@Param("processId") Long processId);

    Optional<ProcessRule> findByProcessIdAndExecutionOrder(Long processId, int executionOrder);

    long countByProcessId(Long processId);
}