package com.xiaoa.quota.mapper;

import com.xiaoa.quota.model.PaymentOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PaymentOrderMapper {

    @Insert("INSERT INTO payment_order (tenant_id, order_no, amount, channel, status, voucher_url, invoice_no) "
            + "VALUES (#{tenantId}, #{orderNo}, #{amount}, #{channel}, 'PENDING', #{voucherUrl}, #{invoiceNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PaymentOrder order);

    @Select("SELECT id, tenant_id, order_no, amount, channel, status, voucher_url, invoice_no, confirm_by, confirmed_at, "
            + "callback_payload, paid_at, created_at, updated_at FROM payment_order WHERE id = #{id}")
    PaymentOrder findById(@Param("id") Long id);

    @Select("SELECT id, tenant_id, order_no, amount, channel, status, voucher_url, invoice_no, confirm_by, confirmed_at, "
            + "callback_payload, paid_at, created_at, updated_at FROM payment_order WHERE order_no = #{orderNo}")
    PaymentOrder findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT id, tenant_id, order_no, amount, channel, status, voucher_url, invoice_no, confirm_by, confirmed_at, "
            + "callback_payload, paid_at, created_at, updated_at FROM payment_order "
            + "WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT #{limit}")
    List<PaymentOrder> findPending(@Param("limit") int limit);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM payment_order "
            + "WHERE tenant_id = #{tenantId} AND status = 'SETTLED'")
    long sumSettledAmount(@Param("tenantId") Long tenantId);

    @Update("UPDATE payment_order SET status = 'SETTLED', confirm_by = #{confirmBy}, confirmed_at = NOW(), "
            + "paid_at = NOW(), invoice_no = COALESCE(#{invoiceNo}, invoice_no) "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int settle(@Param("id") Long id, @Param("confirmBy") Long confirmBy, @Param("invoiceNo") String invoiceNo);

    @Update("UPDATE payment_order SET status = 'CANCELED' WHERE id = #{id} AND status = 'PENDING'")
    int cancel(@Param("id") Long id);
}
