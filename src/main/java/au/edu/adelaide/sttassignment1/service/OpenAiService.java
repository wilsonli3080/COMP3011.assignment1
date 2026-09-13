package au.edu.adelaide.sttassignment1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class OpenAiService {

    public record TranscriptionResult(String text, long inputTokens, long outputTokens) {
    }

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiService() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setBufferRequestBody(true);
        // Increase timeouts to allow OpenAI to process longer audio without premature socket timeouts
        rf.setConnectTimeout(10_000); // 10s connect timeout
        rf.setReadTimeout(30_000);    // 30s read timeout
        this.restTemplate = new RestTemplate(rf);
    }

    public TranscriptionResult transcribe(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String filename = (originalFilename == null || originalFilename.isBlank()) ? "audio.webm" : originalFilename;
        String contentType = file.getContentType() != null && !file.getContentType().isBlank()
                ? file.getContentType()
                : "audio/webm";

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return file.getSize();
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        fileHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(filename)
                .build());

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No audio data was recorded.");
        }

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", new HttpEntity<>(resource, fileHeaders));
        multipart.add("model", "gpt-4o-mini-transcribe");
        multipart.add("response_format", "json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(this.apiKey);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipart, headers);
        String url = "https://api.openai.com/v1/audio/transcriptions";
        String response = restTemplate.postForObject(url, requestEntity, String.class);

        if (response == null || response.isBlank()) {
            return new TranscriptionResult("", 0L, 0L);
        }

        JsonNode node = mapper.readTree(response);

        // Extract transcription text robustly. Different responses may include
        // a top-level "text", or an array of "segments", or nested objects.
        String text = "";
        if (node.has("text")) {
            text = node.path("text").asText("");
        } else if (node.has("segments") && node.path("segments").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : node.path("segments")) {
                String segText = seg.path("text").asText("");
                if (!segText.isBlank()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(segText.trim());
                }
            }
            text = sb.toString();
        } else if (node.has("data") && node.path("data").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node.path("data")) {
                String d = item.path("text").asText("");
                if (!d.isBlank()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(d.trim());
                }
            }
            text = sb.toString();
        }

        // Extract token usage: try common field names, fall back to total_tokens
        JsonNode usageNode = node.path("usage");
        long inputTokens = 0L;
        long outputTokens = 0L;
        if (!usageNode.isMissingNode() && usageNode.size() > 0) {
            inputTokens = firstNonZero(
                    usageNode.path("input_tokens").asLong(0L),
                    usageNode.path("prompt_tokens").asLong(0L),
                    usageNode.path("total_tokens").asLong(0L),
                    usageNode.path("inputTokens").asLong(0L),
                    usageNode.path("totalTokens").asLong(0L)
            );
            outputTokens = firstNonZero(
                    usageNode.path("output_tokens").asLong(0L),
                    usageNode.path("completion_tokens").asLong(0L),
                    usageNode.path("outputTokens").asLong(0L)
            );
        }

        // If still zero, fall back to total_tokens or estimate from text length
        if (inputTokens == 0L && outputTokens == 0L) {
            long totalTokens = usageNode.path("total_tokens").asLong(0L);
            if (totalTokens > 0L) {
                inputTokens = totalTokens;
            } else {
                // rough estimate: 1 token ~= 4 chars (approx tiktoken)
                inputTokens = Math.max(0L, text.length() / 4L);
            }
        }

        return new TranscriptionResult(text, inputTokens, outputTokens);
    }

    private long firstNonZero(long... values) {
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }
}
