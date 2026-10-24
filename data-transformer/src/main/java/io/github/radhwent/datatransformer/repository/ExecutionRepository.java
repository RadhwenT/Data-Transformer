package io.github.radhwent.datatransformer.repository;

import io.github.radhwent.datatransformer.entity.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    /**
     * JOIN FETCH sur "process" : ExecutionResponse.from() lit
     * execution.getProcess().getName(), un accès lazy reel. Sans ce fetch,
     * ca plante en LazyInitializationException des que le controller (non
     * transactionnel, open-in-view=false) essaie de serialiser la reponse.
     */
    @Query("select e from Execution e join fetch e.process where e.user.id = :userId order by e.startedAt desc")
    List<Execution> findByUserIdOrderByStartedAtDesc(@Param("userId") Long userId);

    @Query("select e from Execution e join fetch e.process where e.id = :id")
    Optional<Execution> findByIdWithProcess(@Param("id") Long id);

    Optional<Execution> findFirstByProcessIdOrderByStartedAtDesc(Long processId);

    @Query("select e from Execution e join fetch e.process "
            + "where e.user.id = :userId and e.process.id = :processId order by e.startedAt desc")
    List<Execution> findByUserIdAndProcessIdOrderByStartedAtDesc(
            @Param("userId") Long userId, @Param("processId") Long processId);

    /** Vue admin, toutes utilisateurs confondus (dashboard admin agrege). */
    @Query("select e from Execution e join fetch e.process order by e.startedAt desc")
    List<Execution> findAllOrderByStartedAtDesc();
}