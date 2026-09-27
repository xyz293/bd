package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.UpdateAuditConfigRequest;
import com.xiaoa.admin.dto.UpsertComplianceWordRequest;
import com.xiaoa.admin.mapper.AuditConfigMapper;
import com.xiaoa.admin.mapper.ComplianceWordMapper;
import com.xiaoa.admin.model.AuditConfig;
import com.xiaoa.admin.model.ComplianceWord;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ComplianceService {

    private final ComplianceWordMapper wordMapper;
    private final AuditConfigMapper configMapper;
    private final AdminPermissionService permissionService;

    public ComplianceService(ComplianceWordMapper wordMapper, AuditConfigMapper configMapper,
                             AdminPermissionService permissionService) {
        this.wordMapper = wordMapper;
        this.configMapper = configMapper;
        this.permissionService = permissionService;
    }

    public List<ComplianceWord> words() {
        return wordMapper.findActive(permissionService.requiredRead().getTenantId());
    }

    /**
     * 文本合规过滤（供对话出稿、提示词组装等复用）：
     * level1 违规词替换为 replacement，level2 拦截抛 COMPLIANCE_REJECTED。
     */
    public String filterText(Long tenantId, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<ComplianceWord> words = wordMapper.findActive(tenantId);
        String checked = text;
        for (ComplianceWord word : words) {
            if (word.getWord() == null || !checked.contains(word.getWord())) {
                continue;
            }
            if (word.getLevel() != null && word.getLevel() == 2) {
                throw new BusinessException(ErrorCode.COMPLIANCE_REJECTED, "内容包含违规表述，请调整后重试");
            }
            checked = checked.replace(word.getWord(), word.getReplacement() == null ? "" : word.getReplacement());
        }
        return checked;
    }

    @Transactional
    public ComplianceWord saveWord(UpsertComplianceWordRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        permissionService.requireHeadquarters();
        ComplianceWord word = new ComplianceWord();
        word.setId(request.getId());
        word.setTenantId(principal.getTenantId());
        word.setWord(request.getWord());
        word.setLevel(request.getLevel());
        word.setReplacement(request.getReplacement());
        if (request.getId() == null) {
            word.setCreatedBy(principal.getUserId());
            wordMapper.insert(word);
        } else if (wordMapper.update(word) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合规词不存在");
        }
        return word;
    }

    @Transactional
    public void disableWord(Long id) {
        permissionService.requireHeadquarters();
        wordMapper.disable(permissionService.required().getTenantId(), id);
    }

    @Transactional
    public AuditConfig updateAuditConfig(Long orgId, UpdateAuditConfigRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        if (!"HQ_ADMIN".equals(principal.getRole()) && !principal.getOrgId().equals(orgId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        configMapper.upsert(principal.getTenantId(), orgId, request.getEnabled() ? 1 : 0, principal.getUserId());
        return new AuditConfig(orgId, request.getEnabled());
    }

    public AuditConfig getAuditConfig(Long orgId) {
        AuthPrincipal principal = permissionService.requiredRead();
        Integer enabled = configMapper.findEnabled(principal.getTenantId(), orgId);
        if (enabled == null && orgId != 0L) {
            enabled = configMapper.findEnabled(principal.getTenantId(), 0L);
        }
        return new AuditConfig(orgId, enabled != null && enabled == 1);
    }
}
