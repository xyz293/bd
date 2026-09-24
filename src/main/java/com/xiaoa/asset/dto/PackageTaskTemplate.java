package com.xiaoa.asset.dto;

import java.util.List;

/**
 * 内容包任务模板。创建时强校验 actionType/platform/frequency/judgeType/endTime；
 * 下发时对历史模板宽松处理（缺省 actionType=1、judgeType=1、targetScope=1）。
 */
public class PackageTaskTemplate {

    /** 任务标题，缺省用内容包名称 */
    private String title;
    /** 1 固定动作 / 2 指定内容（对应任务 formType） */
    private Integer actionType;
    private String platform;
    /** 1 每日 / 2 每周 / 3 每月 */
    private Integer frequency;
    /** 1 直接完成 / 2 需截图凭证 */
    private Integer judgeType;
    /** yyyy-MM-dd HH:mm:ss */
    private String endTime;
    /** 1 全员 / 2 区域 / 3 门店 / 4 员工，缺省 1 */
    private Integer targetScope;
    private List<Long> targetIds;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getActionType() { return actionType; }
    public void setActionType(Integer actionType) { this.actionType = actionType; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public Integer getJudgeType() { return judgeType; }
    public void setJudgeType(Integer judgeType) { this.judgeType = judgeType; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Integer getTargetScope() { return targetScope; }
    public void setTargetScope(Integer targetScope) { this.targetScope = targetScope; }
    public List<Long> getTargetIds() { return targetIds; }
    public void setTargetIds(List<Long> targetIds) { this.targetIds = targetIds; }
}
