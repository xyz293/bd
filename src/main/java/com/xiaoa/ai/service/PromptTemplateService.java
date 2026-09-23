package com.xiaoa.ai.service;

import com.xiaoa.ai.dto.PromptTemplateRequest;
import com.xiaoa.ai.mapper.PromptTemplateMapper;
import com.xiaoa.ai.model.PromptTemplate;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromptTemplateService {

    private final PromptTemplateMapper templateMapper;
    private final AdminPermissionService permissionService;

    public PromptTemplateService(PromptTemplateMapper templateMapper, AdminPermissionService permissionService) {
        this.templateMapper = templateMapper;
        this.permissionService = permissionService;
    }

    public List<PromptTemplate> list() {
        permissionService.requiredRead();
        return templateMapper.findAll();
    }

    @Transactional
    public PromptTemplate save(PromptTemplateRequest request) {
        permissionService.requireHeadquarters();
        String scene = normalizeScene(request.getScene());
        PromptTemplate template = new PromptTemplate();
        template.setScene(scene);
        template.setTemplate(request.getTemplate());
        template.setVersion(templateMapper.maxVersion(scene) + 1);
        templateMapper.disableActive(scene);
        templateMapper.insert(template);
        return template;
    }

    @Transactional
    public void rollback(Long id) {
        permissionService.requireHeadquarters();
        PromptTemplate target = templateMapper.findById(id);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提示词模板不存在");
        }
        templateMapper.disableScene(target.getScene());
        if (templateMapper.enable(id) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模板回滚失败");
        }
    }

    private String normalizeScene(String scene) {
        String normalized = scene == null ? "" : scene.trim().toUpperCase();
        if (!"IMAGE".equals(normalized) && !"VIDEO".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "模板场景仅支持 IMAGE 或 VIDEO");
        }
        return normalized;
    }
}
