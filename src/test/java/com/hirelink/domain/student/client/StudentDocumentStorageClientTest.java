package com.hirelink.domain.student.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class StudentDocumentStorageClientTest {
	@Test void onlyUploadsToPrivateBucketAndUsesUniqueNonUpsertPaths() throws Exception {
		var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
		List<String> calls=new ArrayList<>(); String[] bucket={"{\"public\":false}"};
		server.createContext("/storage/v1/",exchange->{
			String path=exchange.getRequestURI().getPath(); calls.add(exchange.getRequestMethod()+" "+path);
			assertThat(exchange.getRequestHeaders().getFirst("apikey")).isEqualTo("test-secret");
			String incoming=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
			if(exchange.getRequestMethod().equals("POST")) {
				assertThat(incoming).isEqualTo("%PDF-test"); assertThat(exchange.getRequestHeaders().getFirst("x-upsert")).isEqualTo("false");
			}
			if(exchange.getRequestMethod().equals("DELETE")) { assertThat(incoming).contains("prefixes","enrollment/1/"); }
			byte[] bytes=(path.contains("/bucket/")?bucket[0]:"{}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,bytes.length);
			exchange.getResponseBody().write(bytes); exchange.close();
		}); server.start();
		try {
			var client=new StudentDocumentStorageClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-secret","student-documents");
			String key="enrollment/1/00000000-0000-0000-0000-000000000000";
			byte[] data="%PDF-test".getBytes(StandardCharsets.UTF_8);
			client.upload(key,data,"application/pdf"); client.delete(key);
			assertThat(calls).containsExactly("GET /storage/v1/bucket/student-documents","POST /storage/v1/object/student-documents/"+key,"DELETE /storage/v1/object/student-documents");
			for(String info:List.of("{\"public\":true}","{}","null")) {
				calls.clear(); bucket[0]=info;
				assertThatThrownBy(()->client.upload(key,data,"application/pdf")).isInstanceOf(StudentDocumentException.class);
				assertThat(calls).hasSize(1);
			}
			calls.clear();
			assertThatThrownBy(()->client.delete("../other-original")).isInstanceOf(StudentDocumentException.class);
			assertThat(calls).isEmpty();
		} finally { server.stop(0); }
	}
}
