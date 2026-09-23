package com.xiaoa.admin.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ReviewAssetRequest {

    @NotNull @Min(2) @Max(3)
    private Integer recommendStatus;

    public Integer getRecommendStatus() { return recommendStatus; }
    public void setRecommendStatus(Integer recommendStatus) { this.recommendStatus = recommendStatus; }
}
