package io.github.radhwent.datatransformer.repository;

import io.github.radhwent.datatransformer.entity.DataProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataProcessRepository extends JpaRepository<DataProcess, Long> {
    Optional<DataProcess> findByName(String name);
    List<DataProcess> findByActiveTrue();
    List<DataProcess> findByUserIdAndActiveTrue(Long userId);
    Optional<DataProcess> findByIdAndActiveTrue(Long id);
    Optional<DataProcess> findByUserIdAndNameAndActiveTrue(Long userId, String name);
}