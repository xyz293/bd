package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.UserWechatBind;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface UserWechatBindMapper {

    @Insert("INSERT INTO user_wechat_bind (user_id, openid, action) VALUES (#{userId}, #{openid}, #{action})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserWechatBind bind);
}
