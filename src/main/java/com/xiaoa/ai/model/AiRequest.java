package com.xiaoa.ai.model;

import java.util.ArrayList;
import java.util.List;

public class AiRequest {

    private String prompt;
    private String styleName;
    private List<String> refImageUrls = new ArrayList<>();

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getStyleName() {
        return styleName;
    }

    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }

    public List<String> getRefImageUrls() {
        return refImageUrls;
    }

    public void setRefImageUrls(List<String> refImageUrls) {
        this.refImageUrls = refImageUrls == null ? new ArrayList<>() : refImageUrls;
    }
}
