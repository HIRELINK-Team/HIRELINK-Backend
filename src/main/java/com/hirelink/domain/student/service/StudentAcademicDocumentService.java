package com.hirelink.domain.student.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.student.client.AcademicOcrClient;
import com.hirelink.domain.student.client.StudentDocumentStorageClient;
import com.hirelink.domain.student.dto.AcademicDocumentResponse;
import com.hirelink.domain.student.entity.StudentDocumentId;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.repository.StudentEnrollmentDocumentRepository;
import com.hirelink.domain.student.repository.StudentDocumentCleanupRepository;
import com.hirelink.global.exception.StudentDocumentException;

@Service
public class StudentAcademicDocumentService {
    private final StudentBadgeService badges;
    private final StudentEnrollmentService common;
    private final StudentEnrollmentDocumentRepository documents;
    private final StudentDocumentCleanupService cleaner;
    private final StudentDocumentCleanupRepository cleanup;
    private final AcademicOcrClient ocr;
    private final StudentDocumentStorageClient storage;
    public StudentAcademicDocumentService(StudentEnrollmentService common,StudentEnrollmentDocumentRepository documents,
            StudentDocumentCleanupService cleaner,StudentDocumentCleanupRepository cleanup,AcademicOcrClient ocr,StudentDocumentStorageClient storage, StudentBadgeService badges) {
        this.badges=badges;
        this.common=common; this.documents=documents; this.cleaner=cleaner; this.cleanup=cleanup; this.ocr=ocr; this.storage=storage;
    }
    @Transactional(timeout=120)
    public AcademicDocumentResponse preview(Long userId,String kind,MultipartFile file) {
        String type=type(kind); common.student(userId);
        byte[] bytes=common.read(file);
        try {
            String contentType=common.contentType(file,bytes);
            var value=ocr.recognize(type,bytes,contentType); value.validate(type);
            UUID id=UUID.randomUUID(); String key=kind+"/"+userId+"/"+id;
            cleaner.reserve(key); storage.upload(key,bytes,contentType);
            var doc=documents.findById(new StudentDocumentId(userId,type)).orElseGet(()->new StudentEnrollmentDocument(userId,type));
            common.retire(doc.getDraftObjectKey()); doc.stageAcademic(id,key,value);
            documents.saveAndFlush(doc); cleanup.deleteById(key);
            return new AcademicDocumentResponse(type,id,value,null);
        } finally { Arrays.fill(bytes,(byte)0); }
    }
    @Transactional(timeout=120)
    public AcademicDocumentResponse confirm(Long userId,String kind,UUID draft) {
        String type=type(kind); var profile=common.student(userId);
        var doc=documents.findById(new StudentDocumentId(userId,type)).orElseThrow(this::missing);
        if(!draft.equals(doc.getDraftId())) throw missing();
        var value=doc.academicExtraction(); value.validate(type);
        common.retire(doc.getObjectKey()); doc.confirmAcademic(Instant.now());
        if(type.equals("TRANSCRIPT")) profile.updateTranscript(value.averageGpa()); else profile.updateVerifiedTopik(value.topikLevel());
        documents.flush();
        badges.issueIfEligible(profile,false);
        return new AcademicDocumentResponse(type,null,doc.confirmedAcademic(),doc.getRegisteredAt());
    }
    @Transactional
    public AcademicDocumentResponse current(Long userId,String kind) {
        String type=type(kind); common.student(userId);
        var doc=documents.findById(new StudentDocumentId(userId,type)).orElseThrow(this::notRegistered);
        if(doc.getRegisteredAt()==null) throw notRegistered();
        return new AcademicDocumentResponse(type,null,doc.confirmedAcademic(),doc.getRegisteredAt());
    }
    private String type(String kind) {
        if(!Set.of("transcript","topik").contains(kind)) throw new StudentDocumentException(HttpStatus.BAD_REQUEST,"INVALID_DOCUMENT_TYPE","서류 종류를 확인해주세요.");
        return kind.toUpperCase(Locale.ROOT);
    }
    private StudentDocumentException missing() { return new StudentDocumentException(HttpStatus.CONFLICT,"DOCUMENT_DRAFT_NOT_FOUND","본인의 최신 추출 결과를 다시 확인해주세요."); }
    private StudentDocumentException notRegistered() { return new StudentDocumentException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_REGISTERED","최종 등록한 서류가 없습니다."); }
}
