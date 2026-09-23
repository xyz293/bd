package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.Tenant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface TenantMapper {

    @Insert("INSERT INTO `tenant` (name, type, industry, asset_package_id, status, expire_at, deleted) "
            + "VALUES (#{name}, #{type}, #{industry}, #{assetPackageId}, #{status}, #{expireAt}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Tenant tenant);

    @Select("SELECT id, name, type, industry, asset_package_id, status, expire_at, created_at, updated_at "
            + "FROM `tenant` WHERE id = #{id} AND deleted = 0")
    Tenant findById(Long id);

    @Update("UPDATE `tenant` SET expire_at = #{expireAt}, status = #{status} WHERE id = #{id} AND deleted = 0")
    int renew(@org.apache.ibatis.annotations.Param("id") Long id,
              @org.apache.ibatis.annotations.Param("expireAt") LocalDateTime expireAt,
              @org.apache.ibatis.annotations.Param("status") Integer status);

    @Update("UPDATE `tenant` SET status = #{status} WHERE id = #{id} AND deleted = 0")
    int updateStatus(@org.apache.ibatis.annotations.Param("id") Long id,
                     @org.apache.ibatis.annotations.Param("status") Integer status);
}
