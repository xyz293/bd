package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO `user` (phone, openid, nickname, status, deleted) "
            + "VALUES (#{phone}, #{openid}, #{nickname}, 1, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserAccount user);

    @Select("SELECT id, phone, openid, nickname, status, created_at, updated_at "
            + "FROM `user` WHERE id = #{id} AND deleted = 0")
    UserAccount findById(Long id);

    @Select("SELECT id, phone, openid, nickname, status, created_at, updated_at "
            + "FROM `user` WHERE openid = #{openid} AND deleted = 0 LIMIT 1")
    UserAccount findByOpenid(String openid);

    @Select("SELECT id, phone, openid, nickname, status, created_at, updated_at "
            + "FROM `user` WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    UserAccount findByPhone(String phone);

    @Update("UPDATE `user` SET openid = #{openid} WHERE id = #{id} AND deleted = 0")
    int updateOpenid(@org.apache.ibatis.annotations.Param("id") Long id,
                     @org.apache.ibatis.annotations.Param("openid") String openid);

    @Update("UPDATE `user` SET status = #{status} WHERE id = #{id} AND deleted = 0")
    int updateStatus(@org.apache.ibatis.annotations.Param("id") Long id,
                     @org.apache.ibatis.annotations.Param("status") Integer status);

    @Select("SELECT DISTINCT u.id, u.phone, u.openid, u.nickname, u.status, u.created_at, u.updated_at "
            + "FROM `user` u JOIN user_org_role r ON r.user_id = u.id "
            + "WHERE r.tenant_id = #{tenantId} AND r.status = 1 AND u.deleted = 0 "
            + "AND (#{orgId} IS NULL OR r.org_id = #{orgId}) ORDER BY u.id LIMIT #{offset}, #{limit}")
    List<UserAccount> findByTenantOrg(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId,
                                      @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(DISTINCT u.id) FROM `user` u JOIN user_org_role r ON r.user_id = u.id "
            + "WHERE r.tenant_id = #{tenantId} AND r.status = 1 AND u.deleted = 0 "
            + "AND (#{orgId} IS NULL OR r.org_id = #{orgId})")
    long countByTenantOrg(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);
}
