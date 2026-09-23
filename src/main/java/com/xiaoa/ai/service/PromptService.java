package com.xiaoa.ai.service;

import com.xiaoa.admin.mapper.ComplianceWordMapper;
import com.xiaoa.ai.mapper.StyleAiMapper;
import com.xiaoa.admin.model.ComplianceWord;
import com.xiaoa.admin.model.StyleOption;
import com.xiaoa.ai.mapper.PromptTemplateMapper;
import com.xiaoa.ai.model.PromptTemplate;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptService {

    private final PromptTemplateMapper templateMapper;
    private final StyleAiMapper styleMapper;
    private final ComplianceWordMapper complianceWordMapper;

    public PromptService(PromptTemplateMapper templateMapper, StyleAiMapper styleMapper,
                         ComplianceWordMapper complianceWordMapper) {
        this.templateMapper = templateMapper;
        this.styleMapper = styleMapper;
        this.complianceWordMapper = complianceWordMapper;
    }

    public PromptBuildResult build(Long tenantId, String scene, String platform, Long styleId,
                                   String productName, String userInput) {
        PromptTemplate template = templateMapper.findActive(scene);
        if (template == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI提示词模板未配置");
        }
        StyleOption style = styleMapper.findVisible(tenantId, styleId);
        if (style == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在或已停用");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("platform", safe(platform));
        variables.put("style", safe(style.getName()));
        variables.put("productName", safe(productName));
        variables.put("userInput", safe(userInput));
        variables.put("scriptRef", "");
        String prompt = template.getTemplate();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        prompt = checkCompliance(tenantId, prompt);
        return new PromptBuildResult(template, style, prompt);
    }

    private String checkCompliance(Long tenantId, String prompt) {
        List<ComplianceWord> words = complianceWordMapper.findActive(tenantId);
        String checked = prompt;
        for (ComplianceWord word : words) {
            if (word.getWord() == null || !checked.contains(word.getWord())) {
                continue;
            }
            if (word.getLevel() != null && word.getLevel() == 2) {
                throw new BusinessException(ErrorCode.COMPLIANCE_REJECTED, "提示词包含违规内容");
            }
            checked = checked.replace(word.getWord(), word.getReplacement() == null ? "" : word.getReplacement());
        }
        return checked;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class PromptBuildResult {
        private final PromptTemplate template;
        private final StyleOption style;
        private final String prompt;

        public PromptBuildResult(PromptTemplate template, StyleOption style, String prompt) {
            this.template = template;
            this.style = style;
            this.prompt = prompt;
        }

        public PromptTemplate getTemplate() { return template; }
        public StyleOption getStyle() { return style; }
        public String getPrompt() { return prompt; }
    }
}
