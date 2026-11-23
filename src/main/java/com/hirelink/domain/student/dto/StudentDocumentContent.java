package com.hirelink.domain.student.dto;

import org.springframework.http.MediaType;

public record StudentDocumentContent(byte[] bytes, MediaType contentType) {
}
