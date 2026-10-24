package io.github.radhwent.datatransformer.repository;

import io.github.radhwent.datatransformer.entity.DataFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataFileRepository extends JpaRepository<DataFile, Long> {
    List<DataFile> findByExecutionId(Long executionId);
}