package com.hirelink.domain.student.entity;

import java.io.Serializable;
import java.util.Objects;

public class StudentDocumentId implements Serializable {
    public Long userId;
    public String documentType;
    public StudentDocumentId() { }
    public StudentDocumentId(Long userId, String documentType) { this.userId=userId; this.documentType=documentType; }
    @Override public boolean equals(Object o) { return o instanceof StudentDocumentId d && Objects.equals(userId,d.userId) && Objects.equals(documentType,d.documentType); }
    @Override public int hashCode() { return Objects.hash(userId,documentType); }
}
