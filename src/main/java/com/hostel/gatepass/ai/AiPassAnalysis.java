package com.hostel.gatepass.ai;

/**
 * Immutable value object holding the result of a Gemini AI risk analysis
 * for a hostel outpass request.
 */
public class AiPassAnalysis {

    private final String riskLevel;
    private final String summary;
    private final boolean autoApprove;

    public AiPassAnalysis(String riskLevel, String summary, boolean autoApprove) {
        this.riskLevel = riskLevel;
        this.summary = summary;
        this.autoApprove = autoApprove;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isAutoApprove() {
        return autoApprove;
    }
}
