package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.InviteCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InviteCodeMapper {

    @Insert("INSERT INTO invite_code (tenant_id, store_id, code, role, expire_at, used, created_by) "
            + "VALUES (#{tenantId}, #{storeId}, #{code}, #{role}, #{expireAt}, 0, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InviteCode inviteCode);

    @Select("SELECT id, tenant_id, store_id, code, role, expire_at, used, created_by, created_at "
            + "FROM invite_code WHERE code = #{code} LIMIT 1")
    InviteCode findByCode(String code);

    @Update("UPDATE invite_code SET used = 1 WHERE id = #{id} AND used = 0 AND expire_at > #{now}")
    int consume(@org.apache.ibatis.annotations.Param("id") Long id,
                @org.apache.ibatis.annotations.Param("now") LocalDateTime now);
}
