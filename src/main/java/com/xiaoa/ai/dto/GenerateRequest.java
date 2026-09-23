package com.xiaoa.ai.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class GenerateRequest {

    @NotBlank
    private String type;
    @NotBlank
    @Size(max = 32)
    private String platform;
    @NotNull
    private Long styleId;
    @Size(max = 2000)
    private String productName;
    @Size(max = 4000)
    private String userInput;
    @Size(max = 10)
    private List<String> refImageUrls = new ArrayList<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Long getStyleId() { return styleId; }
    public void setStyleId(Long styleId) { this.styleId = styleId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public List<String> getRefImageUrls() { return refImageUrls; }
    public void setRefImageUrls(List<String> refImageUrls) { this.refImageUrls = refImageUrls == null ? new ArrayList<>() : refImageUrls; }
}
