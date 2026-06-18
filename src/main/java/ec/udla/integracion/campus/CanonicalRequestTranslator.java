package ec.udla.integracion.campus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class CanonicalRequestTranslator implements Processor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        String originalMessage = exchange.getIn().getBody(String.class);
        JsonNode originalJson = objectMapper.readTree(originalMessage);
        boolean isValid = hasText(originalJson, "request_id") &&
                hasText(originalJson, "student_name") &&
                hasText(originalJson, "student_document") &&
                hasText(originalJson, "request_type") &&
                hasText(originalJson, "channel") &&
                hasText(originalJson, "created_at");
        ObjectNode canonicalJson = objectMapper.createObjectNode();
        if (!isValid) {
            canonicalJson.put("status", "INVALID");
            canonicalJson.put("reason", "Mensaje incompleto o con campos obligatorios ausentes");
            canonicalJson.set("originalMessage", originalJson);
            exchange.setProperty("requestType", "INVALID");
            exchange.getIn().setBody(objectMapper.writeValueAsString(canonicalJson));
            return;
        }
        String requestType = originalJson.get("request_type").asText();
        ObjectNode student = objectMapper.createObjectNode();
        student.put("fullName",
                originalJson.get("student_name").asText());
        student.put("document",
                originalJson.get("student_document").asText());
        canonicalJson.put("requestId",
                originalJson.get("request_id").asText());
        canonicalJson.set("student", student);
        canonicalJson.put("type", requestType);
        canonicalJson.put("sourceChannel",
                originalJson.get("channel").asText());
        canonicalJson.put("createdAt",
                originalJson.get("created_at").asText());
        exchange.setProperty("requestType", requestType);
        exchange.getIn().setBody(objectMapper.writeValueAsString(canonicalJson));
    }

    private boolean hasText(JsonNode jsonNode, String fieldName) {
        return jsonNode.has(fieldName)
                && !jsonNode.get(fieldName).isNull()
                && !jsonNode.get(fieldName).asText().isBlank();
    }
}