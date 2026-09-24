package com.xiaoa.quota.mapper;

import com.xiaoa.quota.model.PlatformUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformUserMapper {

    @Select("SELECT id, login_name, credential_hash, role, status FROM platform_user WHERE login_name = #{loginName}")
    PlatformUser findByLoginName(String loginName);
}
