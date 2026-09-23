package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
