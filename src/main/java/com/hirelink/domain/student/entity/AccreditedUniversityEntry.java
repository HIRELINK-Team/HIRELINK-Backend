package com.hirelink.domain.student.entity;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accredited_university_entry")
public class AccreditedUniversityEntry {
    @Id private Long id;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(nullable = false, length = 20) private String category;
    @Column(name = "listed_name", nullable = false, length = 150) private String listedName;
    @Column(name = "normalized_name", nullable = false, length = 150) private String normalizedName;
    protected AccreditedUniversityEntry() { }
}
