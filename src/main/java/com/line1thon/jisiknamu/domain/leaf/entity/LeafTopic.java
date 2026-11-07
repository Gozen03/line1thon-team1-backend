package com.line1thon.jisiknamu.domain.leaf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "leaf_topics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeafTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leaf_id", nullable = false)
    private Leaf leaf;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status;

    @Builder
    private LeafTopic(Leaf leaf, String topic, TopicStatus status) {
        this.leaf = leaf;
        this.topic = topic;
        this.status = status;
    }

    public void recover() {
        this.status = TopicStatus.RECOVERED;
    }
}
