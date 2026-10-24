package io.github.radhwent.datatransformer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "DT_process_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"process_id", "execution_order"}),
    indexes = @Index(name = "idx_process_rules_rule", columnList = "rule_id")
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_order", nullable = false)
    private int executionOrder;

    @Column(name = "source_field", nullable = false, length = 100)
    private String sourceField;

    @Column(name = "source_field_2", length = 100)
    private String sourceField2;

    @Column(name = "target_field", length = 100)
    private String targetField;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private DataProcess process;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;
}