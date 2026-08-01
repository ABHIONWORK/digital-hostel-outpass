package com.hostel.gatepass.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Service that integrates with the Google Gemini API to provide AI-powered
 * analysis and text generation for the hostel outpass system.
 * Uses Java's built-in HttpClient — no extra dependencies required.
 */
@Service
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final HttpClient httpClient;
    private final String apiKey;

    public GeminiService(@Value("${gemini.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Analyses a pass request and returns a risk level + warden advisory note.
     *
     * @param studentId    Student identifier
     * @param reason       Student-provided reason for leave
     * @param outTime      Departure time
     * @param expectedInTime Expected return time
     * @return AiPassAnalysis containing riskLevel (LOW/MEDIUM/HIGH) and summary
     */
    public AiPassAnalysis analysePassRequest(String studentId, String reason,
                                             LocalDateTime outTime, LocalDateTime expectedInTime) {
        long durationHours = ChronoUnit.HOURS.between(outTime, expectedInTime);
        int departureHour = outTime.getHour();

        String prompt = "You are an AI assistant for a campus hostel warden management system. " +
                "Analyse the following student leave request and return ONLY a valid JSON object " +
                "(no markdown, no explanation) with exactly two fields:\n" +
                "  \"riskLevel\": one of \"LOW\", \"MEDIUM\", or \"HIGH\"\n" +
                "  \"summary\": a single neutral sentence (max 20 words) advising the warden\n\n" +
                "Risk criteria:\n" +
                "  LOW: clear legitimate reason, duration under 12 hours, normal daytime departure\n" +
                "  MEDIUM: vague reason OR duration 12-36 hours OR late-night departure (after 9 PM)\n" +
                "  HIGH: very vague/suspicious reason OR duration over 36 hours OR departure after midnight\n\n" +
                "Student ID: " + studentId + "\n" +
                "Reason: " + reason + "\n" +
                "Duration: " + durationHours + " hours\n" +
                "Departure hour: " + departureHour + ":00\n\n" +
                "Respond with only the JSON object.";

        String rawResponse = callGemini(prompt);
        return parseAnalysis(rawResponse);
    }

    /**
     * Rewrites a student's rough leave reason into a formal, professional sentence.
     *
     * @param roughReason The student's informal or incomplete reason
     * @return A formal, single-sentence reason suitable for official records
     */
    public String suggestFormalReason(String roughReason) {
        String prompt = "Rewrite the following hostel leave reason as a single, formal, " +
                "professional sentence suitable for an official campus leave record. " +
                "Keep it concise (under 20 words). Return only the rewritten sentence, nothing else.\n\n" +
                "Original reason: " + roughReason;

        String response = callGemini(prompt);
        return response.trim().replaceAll("^[\"']|[\"']$", "");
    }

    /**
     * Calls the Gemini REST API with the given prompt.
     * Returns the raw text content of the first candidate part, or an empty string on failure.
     */
    private String callGemini(String prompt) {
        String requestBody = "{\n" +
                "  \"contents\": [{\n" +
                "    \"parts\": [{\"text\": " + escapeJson(prompt) + "}]\n" +
                "  }],\n" +
                "  \"generationConfig\": {\n" +
                "    \"temperature\": 0.3,\n" +
                "    \"maxOutputTokens\": 150\n" +
                "  }\n" +
                "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractTextFromResponse(response.body());
            } else {
                System.err.println("[GeminiService] API returned status " + response.statusCode() + ": " + response.body());
                return "";
            }
        } catch (Exception e) {
            System.err.println("[GeminiService] Error calling Gemini API: " + e.getMessage());
            return "";
        }
    }

    /**
     * Extracts the plain text value from the Gemini JSON response.
     * Parses the "text" field inside candidates[0].content.parts[0] without
     * an external JSON library — keeping dependencies at zero.
     */
    private String extractTextFromResponse(String jsonBody) {
        String textMarker = "\"text\":";
        int markerIndex = jsonBody.indexOf(textMarker);
        if (markerIndex < 0) {
            return "";
        }

        int start = jsonBody.indexOf("\"", markerIndex + textMarker.length());
        if (start < 0) {
            return "";
        }

        // Walk forward to find the closing quote, respecting escaped quotes
        StringBuilder result = new StringBuilder();
        for (int i = start + 1; i < jsonBody.length(); i++) {
            char c = jsonBody.charAt(i);
            if (c == '\\' && i + 1 < jsonBody.length()) {
                char next = jsonBody.charAt(i + 1);
                if (next == '"') {
                    result.append('"');
                } else if (next == 'n') {
                    result.append('\n');
                } else if (next == 't') {
                    result.append('\t');
                } else {
                    result.append(next);
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString().trim();
    }

    /**
     * Parses the JSON response from the risk-analysis prompt into an AiPassAnalysis.
     * Falls back gracefully if JSON cannot be parsed (e.g., API unavailable).
     */
    private AiPassAnalysis parseAnalysis(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new AiPassAnalysis("LOW", "AI analysis unavailable; please review manually.");
        }

        // Strip optional markdown code fences
        String cleaned = rawText.replace("```json", "").replace("```", "").trim();

        String riskLevel = extractJsonString(cleaned, "riskLevel");
        String summary = extractJsonString(cleaned, "summary");

        // Validate riskLevel against allowed values
        if (!riskLevel.equals("LOW") && !riskLevel.equals("MEDIUM") && !riskLevel.equals("HIGH")) {
            riskLevel = "LOW";
        }

        if (summary.isBlank()) {
            summary = "AI analysis incomplete; please review manually.";
        }

        return new AiPassAnalysis(riskLevel, summary);
    }

    /**
     * Extracts a string value from a simple flat JSON object by key name.
     * Uses indexed character scanning instead of an external library.
     */
    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex < 0) {
            return "";
        }

        int quoteStart = json.indexOf("\"", colonIndex + 1);
        if (quoteStart < 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                value.append(json.charAt(i + 1));
                i++;
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
        }
        return value.toString().trim();
    }

    /**
     * Escapes a string for safe embedding as a JSON string value.
     */
    private String escapeJson(String text) {
        if (text == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
