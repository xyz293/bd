package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 问卷作答提交（方案 ⑨ /chat/answer）：questionId + 选项值，
 * 服务端按 slotKey 合并进槽位后回 ② 重新计算缺口。
 */
public class ChatAnswerRequest {

    @NotEmpty(message = "答案不能为空")
    @Size(max = 3, message = "单轮最多回答3题")
    private List<AnswerItem> answers = new ArrayList<>();

    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

    public static class AnswerItem {

        /** 对应槽位 key（与 QuestionVO.slotKey 一致） */
        @NotBlank(message = "slotKey不能为空")
        @Size(max = 32)
        private String slotKey;

        /** 选中的选项值（选项 label / 自由输入文本） */
        @NotBlank(message = "value不能为空")
        @Size(max = 200)
        private String value;

        public String getSlotKey() { return slotKey; }
        public void setSlotKey(String slotKey) { this.slotKey = slotKey; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
