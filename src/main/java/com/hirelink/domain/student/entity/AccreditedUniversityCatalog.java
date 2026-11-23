package com.hirelink.domain.student.entity;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accredited_university_catalog")
public class AccreditedUniversityCatalog {
    @Id @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "source_name", nullable = false, length = 250) private String sourceName;
    @Column(name = "expected_entry_count", nullable = false) private int expectedEntryCount;
    protected AccreditedUniversityCatalog() { }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public int getExpectedEntryCount() { return expectedEntryCount; }
}
