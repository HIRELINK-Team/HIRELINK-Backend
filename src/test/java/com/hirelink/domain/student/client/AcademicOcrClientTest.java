package com.hirelink.domain.student.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class AcademicOcrClientTest {
    @Test void validatesTypedExtractionAndRejectsMalformedPrivateUpstreamContent() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        int[] status={200}; String[] body={"{\"academicYear\":2025,\"semester\":\"SECOND\",\"averageGpa\":\"4.24\",\"topikLevel\":null}"};
        server.createContext("/student/",exchange->{
            byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status[0],bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            var client=new AcademicOcrClient("http://127.0.0.1:"+server.getAddress().getPort());
            byte[] data="%PDF-test".getBytes(StandardCharsets.UTF_8);
            assertThat(client.recognize("TRANSCRIPT",data,"application/pdf").averageGpa()).isEqualByComparingTo("4.24");
            body[0]="{\"academicYear\":null,\"semester\":null,\"averageGpa\":null,\"topikLevel\":6}";
            assertThat(client.recognize("TOPIK",data,"application/pdf").topikLevel()).isEqualTo((short)6);
            for(String invalid:List.of("{}","null","private-document-content",
                "{\"academicYear\":null,\"semester\":null,\"averageGpa\":null,\"topikLevel\":\"6\"}",
                "{\"academicYear\":null,\"semester\":null,\"averageGpa\":null,\"topikLevel\":65537}")) {
                body[0]=invalid;
                assertThatThrownBy(()->client.recognize("TOPIK",data,"application/pdf")).isInstanceOf(StudentDocumentException.class);
            }
            status[0]=422;
            assertThatThrownBy(()->client.recognize("TRANSCRIPT",data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("UNREADABLE_STUDENT_DOCUMENT");
            status[0]=503;
            assertThatThrownBy(()->client.recognize("TRANSCRIPT",data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("STUDENT_DOCUMENT_OCR_FAILED");
            assertThatThrownBy(()->new AcademicOcrClient("https://example.com").recognize("TOPIK",data,"application/pdf")).isInstanceOf(StudentDocumentException.class);
        } finally {server.stop(0);}
    }
}
