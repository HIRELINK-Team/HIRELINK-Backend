package com.hirelink.domain.student.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class StudentDocumentStorageClientTest {
	private static final String KEY = "topik/1/00000000-0000-0000-0000-000000000000";

	@ParameterizedTest @ValueSource(strings = {"application/pdf", "image/jpeg", "image/png"})
	void downloadsAuthenticatedObjectWithOriginalBytesAndAllowedContentType(String type) throws Exception {
		try (var fixture = new DownloadServer()) {
			fixture.type = type + "; charset=utf-8";
			var content = fixture.client.download(KEY);
			assertThat(content.bytes()).isEqualTo(fixture.bytes);
			assertThat(content.contentType()).isEqualTo(MediaType.parseMediaType(type));
			assertThat(fixture.calls).containsExactly("/storage/v1/bucket/student-documents", "/storage/v1/object/authenticated/student-documents/" + KEY);
		}
	}

	@Test void requiresPrivateBucketBeforeFetchingOriginal() throws Exception {
		try (var fixture = new DownloadServer()) {
			for (String bucket : List.of("{\"public\":true}", "{}", "null")) {
				fixture.calls.clear(); fixture.bucket = bucket;
				assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class);
				assertThat(fixture.calls).containsExactly("/storage/v1/bucket/student-documents");
			}
		}
	}

	@ParameterizedTest @ValueSource(ints = {302, 403, 404, 500})
	void mapsStorageFailureWithoutFollowingRedirectOrExposingResponse(int status) throws Exception {
		try (var fixture = new DownloadServer()) {
			fixture.status = status;
			fixture.bytes = "private storage details".getBytes(StandardCharsets.UTF_8);
			assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class)
					.hasMessage("서류 저장소를 이용할 수 없습니다.");
			assertThat(fixture.calls).hasSize(2).noneMatch(path -> path.contains("redirect-target"));
		}
	}

	@ParameterizedTest @ValueSource(strings = {"text/html", "application/json", "application/octet-stream", ""})
	void rejectsUnexpectedOrMissingContentType(String type) throws Exception {
		try (var fixture = new DownloadServer()) {
			fixture.type = type;
			assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class);
		}
	}

	@Test void enforcesDownloadSizeWithAndWithoutContentLength() throws Exception {
		try (var fixture = new DownloadServer()) {
			fixture.bytes = new byte[0];
			assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class);
			fixture.bytes = new byte[5 * 1024 * 1024];
			assertThat(fixture.client.download(KEY).bytes()).hasSize(5 * 1024 * 1024);
			fixture.bytes = new byte[5 * 1024 * 1024 + 1];
			assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class);
			fixture.chunked = true;
			assertThatThrownBy(() -> fixture.client.download(KEY)).isInstanceOf(StudentDocumentException.class);
		}
	}

	@Test void rejectsUnsupportedPathsWithoutCallingStorage() throws Exception {
		try (var fixture = new DownloadServer()) {
			for (String key : List.of("../original", "registration/1/00000000-0000-0000-0000-000000000000", KEY + "?download=1")) {
				assertThatThrownBy(() -> fixture.client.download(key)).isInstanceOf(StudentDocumentException.class);
			}
			assertThat(fixture.calls).isEmpty();
		}
	}

	private static class DownloadServer implements AutoCloseable {
		private final HttpServer server;
		private final StudentDocumentStorageClient client;
		private final List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
		private String bucket = "{\"public\":false}";
		private String type = "application/pdf";
		private int status = 200;
		private boolean chunked;
		private byte[] bytes = "%PDF-original".getBytes(StandardCharsets.UTF_8);

		private DownloadServer() throws Exception {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/storage/v1/", exchange -> {
				try {
					String path = exchange.getRequestURI().getPath(); calls.add(path);
					boolean metadata = path.contains("/bucket/");
					if (!"test-secret".equals(exchange.getRequestHeaders().getFirst("apikey"))) {
						exchange.sendResponseHeaders(401, -1); return;
					}
					byte[] body = metadata ? bucket.getBytes(StandardCharsets.UTF_8) : bytes;
					String contentType = metadata ? "application/json" : type;
					if (!contentType.isEmpty()) { exchange.getResponseHeaders().set("Content-Type", contentType); }
					if (status == 302 && !metadata) { exchange.getResponseHeaders().set("Location", "/storage/v1/redirect-target"); }
					exchange.sendResponseHeaders(metadata ? 200 : status, body.length == 0 ? -1 : !metadata && chunked ? 0 : body.length);
					if (body.length > 0) { exchange.getResponseBody().write(body); }
				} finally { exchange.close(); }
			});
			server.start();
			client = new StudentDocumentStorageClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-secret", "student-documents");
		}
		@Override public void close() { server.stop(0); }
	}

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
