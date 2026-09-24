package com.xiaoa.asset.service;

import com.xiaoa.ai.service.ObjectStorageService;
import com.xiaoa.asset.dto.AssetUpdateRequest;
import com.xiaoa.asset.dto.ReviewAssetRequest;
import com.xiaoa.asset.mapper.AssetMapper;
import com.xiaoa.asset.model.Asset;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.MessageMapper;
import com.xiaoa.task.model.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 素材服务：上传按 scope 分两条路落库、编辑软删走权限矩阵、推优是 status+scope 的状态迁移。
 */
@Service
public class AssetService {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;

    private final AssetMapper assetMapper;
    private final AssetVisibilityService visibilityService;
    private final ObjectStorageService objectStorageService;
    private final AdminPermissionService permissionService;
    private final MessageMapper messageMapper;

    public AssetService(AssetMapper assetMapper, AssetVisibilityService visibilityService,
                        ObjectStorageService objectStorageService, AdminPermissionService permissionService,
                        MessageMapper messageMapper) {
        this.assetMapper = assetMapper;
        this.visibilityService = visibilityService;
        this.objectStorageService = objectStorageService;
        this.permissionService = permissionService;
        this.messageMapper = messageMapper;
    }

    /**
     * 路 A · 总部上传品牌素材：直接可用（status=APPROVED）。
     */
    @Transactional
    public Asset uploadBrand(MultipartFile file, String name, String category) {
        AuthPrincipal principal = permissionService.required();
        permissionService.requireHeadquarters();
        String type = validateFile(file);
        String url = objectStorageService.save(principal.getTenantId(), null,
                file.getOriginalFilename(), checkedStream(file), file.getSize());
        Asset asset = new Asset();
        asset.setTenantId(principal.getTenantId());
        asset.setScope(Asset.SCOPE_BRAND);
        asset.setType(type);
        asset.setName(resolveName(name, file));
        asset.setContent(url);
        asset.setVersion("v1");
        asset.setCategory(normalizeCategory(category));
        asset.setStatus(Asset.STATUS_APPROVED);
        asset.setUploaderId(principal.getUserId());
        assetMapper.insert(asset);
        return assetMapper.findById(principal.getTenantId(), asset.getId());
    }

    /**
     * 路 B · 门店上传本店素材：即传即用（status=APPROVED）。
     */
    @Transactional
    public Asset uploadStore(MultipartFile file, String name, String category) {
        AuthPrincipal principal = AuthContext.required();
        if (principal.getOrgId() == null || !"STAFF".equals(principal.getRole()) && !"OWNER".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "员工需入店后才能上传素材");
        }
        String type = validateFile(file);
        String url = objectStorageService.save(principal.getTenantId(), principal.getOrgId(),
                file.getOriginalFilename(), checkedStream(file), file.getSize());
        Asset asset = new Asset();
        asset.setTenantId(principal.getTenantId());
        asset.setStoreId(principal.getOrgId());
        asset.setScope(Asset.SCOPE_STORE);
        asset.setType(type);
        asset.setName(resolveName(name, file));
        asset.setContent(url);
        asset.setVersion("v1");
        asset.setCategory(normalizeCategory(category));
        asset.setStatus(Asset.STATUS_APPROVED);
        asset.setUploaderId(principal.getUserId());
        assetMapper.insert(asset);
        return assetMapper.findById(principal.getTenantId(), asset.getId());
    }

    /**
     * 三层可见性查询（创作选素材用）。
     */
    public List<Asset> visible(String category) {
        AuthPrincipal principal = AuthContext.required();
        Long storeId = isStoreRole(principal) ? principal.getOrgId() : null;
        return visibilityService.visibleFor(principal.getTenantId(), storeId, normalizeCategory(category));
    }

    public PageResult<Asset> adminPage(String scope, String status, String category, int pageNo, int pageSize) {
        AuthPrincipal principal = permissionService.requiredRead();
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = Math.max(pageNo, 1) - 1;
        List<Asset> list = assetMapper.selectPage(principal.getTenantId(), scope, status,
                normalizeCategory(category), offset * size, size);
        long total = assetMapper.countPage(principal.getTenantId(), scope, status, normalizeCategory(category));
        return PageResult.of(list, total, Math.max(pageNo, 1), size);
    }

    /**
     * 编辑：行业包素材租户侧只读；品牌层仅总部；本店层仅店长改本店。
     */
    @Transactional
    public void update(Long assetId, AssetUpdateRequest request) {
        AuthPrincipal principal = AuthContext.required();
        Asset asset = requireAsset(principal.getTenantId(), assetId);
        requireManageable(principal, asset);
        if (request.getName() == null && request.getCategory() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "至少提供名称或分类");
        }
        assetMapper.updateNameCategory(principal.getTenantId(), assetId, request.getName(), request.getCategory());
    }

    /**
     * 软删：work 存 url 快照，历史引用不受影响。
     */
    @Transactional
    public void delete(Long assetId) {
        AuthPrincipal principal = AuthContext.required();
        Asset asset = requireAsset(principal.getTenantId(), assetId);
        requireManageable(principal, asset);
        assetMapper.updateStatus(principal.getTenantId(), assetId, Asset.STATUS_DELETED);
    }

    /**
     * 推优：仅本店素材可推优，已在推优流程中的拒绝。
     */
    @Transactional
    public void recommend(Long assetId) {
        AuthPrincipal principal = AuthContext.required();
        if (principal.getOrgId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号未入店，无法推优素材");
        }
        Asset asset = visibilityService.findVisible(principal.getTenantId(), principal.getOrgId(), assetId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在或不可见");
        }
        if (!Asset.SCOPE_STORE.equals(asset.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅本店素材可推优");
        }
        if (assetMapper.markPendingReview(principal.getTenantId(), assetId) != 1) {
            throw new BusinessException(ErrorCode.DUPLICATE, "已在推优流程中");
        }
    }

    /**
     * 总部推优审核：pass 升层 BRAND；reject 置 REJECTED 并通知推荐人（上传人）。
     */
    @Transactional
    public void review(Long assetId, ReviewAssetRequest request) {
        AuthPrincipal principal = permissionService.required();
        permissionService.requireHeadquarters();
        Asset asset = requireAsset(principal.getTenantId(), assetId);
        if (Asset.SCOPE_PLATFORM.equals(asset.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "行业包素材无需审核");
        }
        if (!Asset.STATUS_PENDING_REVIEW.equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材不在待审核状态");
        }
        boolean pass = request.getPass() != null && request.getPass();
        if (pass) {
            if (assetMapper.promote(principal.getTenantId(), assetId, request.getCategory()) != 1) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材状态已变更，请刷新后重试");
            }
            return;
        }
        if (assetMapper.updateStatus(principal.getTenantId(), assetId, Asset.STATUS_REJECTED) != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材状态已变更，请刷新后重试");
        }
        if (asset.getUploaderId() != null) {
            Message message = new Message();
            message.setTenantId(principal.getTenantId());
            message.setUserId(asset.getUploaderId());
            message.setType("ASSET_RECOMMEND");
            message.setTitle("素材推优被驳回");
            message.setContent("你推优的素材「" + asset.getName() + "」未通过审核");
            messageMapper.insert(message);
        }
    }

    private Asset requireAsset(Long tenantId, Long assetId) {
        Asset asset = assetMapper.findById(tenantId, assetId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在");
        }
        return asset;
    }

    private void requireManageable(AuthPrincipal principal, Asset asset) {
        if (Asset.SCOPE_PLATFORM.equals(asset.getScope())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "行业包素材不可修改");
        }
        if (Asset.SCOPE_BRAND.equals(asset.getScope())) {
            if (!"HQ_ADMIN".equals(principal.getRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "品牌素材仅总部管理员可管理");
            }
            return;
        }
        if (!"OWNER".equals(principal.getRole()) || !principal.getOrgId().equals(asset.getStoreId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "本店素材仅店长可管理");
        }
    }

    /**
     * 文件校验（两路共用，上传前）：类型白名单 jpg/png/mp4；图10M、视频100M；失败不落库不留文件。
     */
    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        boolean image = "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext);
        boolean video = "mp4".equals(ext);
        if (!image && !video) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持 jpg/png/mp4 文件");
        }
        if (image && file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片大小不能超过10M");
        }
        if (video && file.getSize() > MAX_VIDEO_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "视频大小不能超过100M");
        }
        return image ? "IMAGE" : "VIDEO";
    }

    private InputStream checkedStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件读取失败");
        }
    }

    private String resolveName(String name, MultipartFile file) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (filename.isEmpty()) {
            return "未命名素材";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String normalizeCategory(String category) {
        return category == null || category.trim().isEmpty() ? "DEFAULT" : category.trim();
    }

    private boolean isStoreRole(AuthPrincipal principal) {
        return "STAFF".equals(principal.getRole()) || "OWNER".equals(principal.getRole());
    }
}
