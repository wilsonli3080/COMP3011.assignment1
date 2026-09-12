package au.edu.adelaide.sttassignment1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        this.restTemplate = new RestTemplate(rf);
    }

    public TranscriptionResult transcribe(MultipartFile file) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                String name = file.getOriginalFilename();
                return (name == null || name.isBlank()) ? "audio.webm" : name;
            }

            @Override
            public long contentLength() {
                return file.getSize();
            }
        };

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", resource);
        multipart.add("model", "gpt-4o-mini-transcribe");

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
        String text = node.path("text").asText("");

        JsonNode usageNode = node.path("usage");
        long inputTokens = firstNonZero(
                usageNode.path("input_tokens").asLong(0L),
                usageNode.path("prompt_tokens").asLong(0L),
                usageNode.path("total_tokens").asLong(0L)
        );
        long outputTokens = firstNonZero(
                usageNode.path("output_tokens").asLong(0L),
                usageNode.path("completion_tokens").asLong(0L),
                0L
        );

        if (inputTokens == 0L && outputTokens == 0L) {
            long totalTokens = usageNode.path("total_tokens").asLong(0L);
            if (totalTokens > 0L) {
                inputTokens = totalTokens;
                outputTokens = 0L;
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
