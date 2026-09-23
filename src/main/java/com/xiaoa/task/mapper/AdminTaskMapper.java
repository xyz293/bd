package com.xiaoa.task.mapper;

import com.xiaoa.task.model.AdminTaskStoreItem;
import com.xiaoa.task.model.TaskRankingItem;
import com.xiaoa.task.model.TaskRecordDetail;
import com.xiaoa.task.model.TaskRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminTaskMapper {

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND task_id = #{taskId} "
            + "AND period_date = #{periodDate}")
    long countExpected(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                       @Param("periodDate") LocalDate periodDate);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND task_id = #{taskId} "
            + "AND period_date = #{periodDate} AND status = 1")
    long countFinished(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                       @Param("periodDate") LocalDate periodDate);

    @Select("SELECT id, tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id, finished_at, created_at "
            + "FROM task_record WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND period_date = #{periodDate} "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) ORDER BY store_id, user_id")
    List<TaskRecord> findRecords(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                                 @Param("periodDate") LocalDate periodDate, @Param("storeId") Long storeId);

    @Select("SELECT COUNT(*) FROM task_record tr JOIN `org` s ON s.id = tr.store_id AND s.tenant_id = tr.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.task_id = #{taskId} AND tr.period_date = #{periodDate} "
            + "AND (#{storeId} IS NULL OR tr.store_id = #{storeId}) "
            + "AND (#{regionId} IS NULL OR s.parent_id = #{regionId})")
    long countExpectedScoped(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                             @Param("periodDate") LocalDate periodDate, @Param("storeId") Long storeId,
                             @Param("regionId") Long regionId);

    @Select("SELECT COUNT(*) FROM task_record tr JOIN `org` s ON s.id = tr.store_id AND s.tenant_id = tr.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.task_id = #{taskId} AND tr.period_date = #{periodDate} "
            + "AND tr.status = 1 AND (#{storeId} IS NULL OR tr.store_id = #{storeId}) "
            + "AND (#{regionId} IS NULL OR s.parent_id = #{regionId})")
    long countFinishedScoped(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                             @Param("periodDate") LocalDate periodDate, @Param("storeId") Long storeId,
                             @Param("regionId") Long regionId);

    @Select("SELECT tr.store_id AS storeId, s.name AS storeName, COUNT(*) AS expected, "
            + "SUM(CASE WHEN tr.status = 1 THEN 1 ELSE 0 END) AS finished "
            + "FROM task_record tr JOIN `org` s ON s.id = tr.store_id AND s.tenant_id = tr.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.task_id = #{taskId} AND tr.period_date = #{periodDate} "
            + "AND (#{storeId} IS NULL OR tr.store_id = #{storeId}) "
            + "AND (#{regionId} IS NULL OR s.parent_id = #{regionId}) "
            + "GROUP BY tr.store_id, s.name ORDER BY tr.store_id")
    List<AdminTaskStoreItem> findStoreSummary(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                                              @Param("periodDate") LocalDate periodDate,
                                              @Param("storeId") Long storeId, @Param("regionId") Long regionId);

    @Select("SELECT tr.id, tr.task_id AS taskId, tr.user_id AS userId, u.nickname, tr.store_id AS storeId, "
            + "s.name AS storeName, tr.period_date AS periodDate, tr.status, tr.publish_record_id AS publishRecordId, "
            + "p.proof_url AS proofUrl, tr.finished_at AS finishedAt "
            + "FROM task_record tr JOIN `user` u ON u.id = tr.user_id "
            + "JOIN `org` s ON s.id = tr.store_id AND s.tenant_id = tr.tenant_id "
            + "LEFT JOIN publish_record p ON p.id = tr.publish_record_id AND p.tenant_id = tr.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.task_id = #{taskId} AND tr.period_date = #{periodDate} "
            + "AND tr.store_id = #{storeId} ORDER BY tr.status, tr.user_id")
    List<TaskRecordDetail> findRecordDetails(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                                              @Param("periodDate") LocalDate periodDate,
                                              @Param("storeId") Long storeId);

    @Select("SELECT r.user_id AS userId, MAX(u.nickname) AS nickname, r.org_id AS storeId, "
            + "MAX(s.name) AS storeName, COUNT(*) AS expected, "
            + "SUM(CASE WHEN tr.status = 1 THEN 1 ELSE 0 END) AS finished, MAX(tr.finished_at) AS lastFinishedAt "
            + "FROM task_record tr JOIN user_org_role r ON r.tenant_id = tr.tenant_id AND r.user_id = tr.user_id "
            + "AND r.org_id = tr.store_id AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') "
            + "JOIN `user` u ON u.id = r.user_id AND u.deleted = 0 "
            + "JOIN `org` s ON s.id = r.org_id AND s.tenant_id = r.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.period_date >= #{from} AND tr.period_date < #{to} "
            + "AND (#{storeId} IS NULL OR tr.store_id = #{storeId}) "
            + "AND (#{regionId} IS NULL OR s.parent_id = #{regionId}) "
            + "GROUP BY r.user_id, r.org_id ORDER BY finished DESC, (finished / NULLIF(COUNT(*), 0)) DESC, "
            + "lastFinishedAt ASC LIMIT #{limit}")
    List<TaskRankingItem> findRanking(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to, @Param("storeId") Long storeId,
                                      @Param("regionId") Long regionId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND period_date >= #{from} AND period_date < #{to}")
    long countUserExpected(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND status = 1 AND period_date >= #{from} AND period_date < #{to}")
    long countUserFinished(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(DISTINCT period_date) FROM task_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND status = 1 AND period_date >= #{from} AND period_date < #{to}")
    long countFinishedDays(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT tr.id, tr.tenant_id, tr.task_id, tr.user_id, tr.store_id, tr.period_date, tr.status, "
            + "tr.publish_record_id, tr.finished_at, tr.created_at "
            + "FROM task_record tr JOIN `org` s ON s.id = tr.store_id AND s.tenant_id = tr.tenant_id "
            + "WHERE tr.tenant_id = #{tenantId} AND tr.task_id = #{taskId} AND tr.period_date = #{periodDate} "
            + "AND (#{storeId} IS NULL OR tr.store_id = #{storeId}) "
            + "AND (#{regionId} IS NULL OR s.parent_id = #{regionId}) ORDER BY tr.store_id, tr.user_id")
    List<TaskRecord> findRecordsScoped(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                                       @Param("periodDate") LocalDate periodDate, @Param("storeId") Long storeId,
                                       @Param("regionId") Long regionId);
}
