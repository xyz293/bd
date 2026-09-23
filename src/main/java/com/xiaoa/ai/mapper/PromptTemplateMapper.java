package com.xiaoa.ai.mapper;

import com.xiaoa.ai.model.PromptTemplate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PromptTemplateMapper {

    @Select("SELECT id, scene, template, version, status, created_at, updated_at FROM prompt_template "
            + "WHERE scene = #{scene} AND status = 1 ORDER BY version DESC LIMIT 1")
    PromptTemplate findActive(@Param("scene") String scene);

    @Select("SELECT id, scene, template, version, status, created_at, updated_at FROM prompt_template "
            + "ORDER BY scene, version DESC, id DESC")
    List<PromptTemplate> findAll();

    @Select("SELECT id, scene, template, version, status, created_at, updated_at FROM prompt_template WHERE id = #{id}")
    PromptTemplate findById(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(version), 0) FROM prompt_template WHERE scene = #{scene}")
    int maxVersion(@Param("scene") String scene);

    @Update("UPDATE prompt_template SET status = 0 WHERE scene = #{scene} AND status = 1")
    int disableActive(@Param("scene") String scene);

    @Insert("INSERT INTO prompt_template (scene, template, version, status) VALUES (#{scene}, #{template}, #{version}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PromptTemplate template);

    @Update("UPDATE prompt_template SET status = 0 WHERE scene = #{scene}")
    int disableScene(@Param("scene") String scene);

    @Update("UPDATE prompt_template SET status = 1 WHERE id = #{id}")
    int enable(@Param("id") Long id);
}
