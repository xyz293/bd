package com.xiaoa.ai.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class PromptTemplateRequest {

    private Long id;
    @NotBlank
    @Size(max = 32)
    private String scene;
    @NotBlank
    private String template;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
}
