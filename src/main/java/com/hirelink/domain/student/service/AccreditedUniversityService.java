package com.hirelink.domain.student.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.hirelink.domain.student.repository.AccreditedUniversityCatalogRepository;
import com.hirelink.domain.student.repository.AccreditedUniversityEntryRepository;
import com.hirelink.global.exception.StudentDocumentException;

@Service
public class AccreditedUniversityService {
    private final AccreditedUniversityCatalogRepository catalogs;
    private final AccreditedUniversityEntryRepository entries;
    public AccreditedUniversityService(AccreditedUniversityCatalogRepository catalogs, AccreditedUniversityEntryRepository entries) {
        this.catalogs = catalogs; this.entries = entries;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Accreditation lookup(String universityName, LocalDate date) {
        var catalog = catalogs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date).orElseThrow(this::unavailable);
        if (entries.countByEffectiveFrom(catalog.getEffectiveFrom()) != catalog.getExpectedEntryCount()) { throw unavailable(); }
        return new Accreditation(entries.existsByEffectiveFromAndNormalizedName(catalog.getEffectiveFrom(), normalize(universityName)), catalog.getEffectiveFrom());
    }
    public static String normalize(String name) {
        if (name == null) { return ""; }
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("^(.*(?:대학교|대학))\\s*[^()]*캠퍼스$", "$1")
                .replaceAll("(?U)\\s+", "").toLowerCase(Locale.ROOT);
    }
    private StudentDocumentException unavailable() {
        return new StudentDocumentException(HttpStatus.SERVICE_UNAVAILABLE, "ACCREDITED_UNIVERSITY_LIST_UNAVAILABLE", "적용 가능한 인증대학 목록을 확인할 수 없습니다.");
    }
    public record Accreditation(boolean accredited, LocalDate effectiveFrom) { }
}
