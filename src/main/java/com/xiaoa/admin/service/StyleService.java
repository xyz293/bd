package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.CreateStyleRequest;
import com.xiaoa.admin.dto.UpdateStyleRequest;
import com.xiaoa.admin.mapper.StyleMapper;
import com.xiaoa.admin.model.StyleOption;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StyleService {

    private final StyleMapper styleMapper;
    private final AdminPermissionService permissionService;

    public StyleService(StyleMapper styleMapper, AdminPermissionService permissionService) {
        this.styleMapper = styleMapper;
        this.permissionService = permissionService;
    }

    public List<StyleOption> visible() {
        return styleMapper.findVisible(permissionService.requiredRead().getTenantId());
    }

    @Transactional
    public StyleOption create(CreateStyleRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        permissionService.requireHeadquarters();
        StyleOption style = new StyleOption();
        style.setTenantId(principal.getTenantId());
        style.setPackageId(request.getPackageId());
        style.setName(request.getName());
        style.setDescription(request.getDescription());
        style.setExampleUrl(request.getExampleUrl());
        style.setSortNo(request.getSortNo());
        styleMapper.insert(style);
        return style;
    }

    @Transactional
    public void update(Long id, UpdateStyleRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        permissionService.requireHeadquarters();
        StyleOption style = new StyleOption();
        style.setId(id);
        style.setTenantId(principal.getTenantId());
        style.setName(request.getName());
        style.setDescription(request.getDescription());
        style.setExampleUrl(request.getExampleUrl());
        style.setSortNo(request.getSortNo());
        if (styleMapper.update(style) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        permissionService.requireHeadquarters();
        styleMapper.delete(permissionService.required().getTenantId(), id);
    }
}
