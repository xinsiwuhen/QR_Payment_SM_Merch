package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.ReconPayBillOrderEntity;
import com.chinaepay.wx.entity.ReconRefundBillOrderEntity;
import com.chinaepay.wx.entity.RefundOrderEntity;

public class ReconRefundBillOrderServlet extends ReconBillOrderServlet {

	@Override
	public ReconBillOrderTask getReconBillOrderTask(ClosableTimer closableTimer, String strReconStartDay,
			String strReconEndDay) {
		return new ReconRefundBillOrderTask(closableTimer, strReconStartDay, strReconEndDay);
	}

	public class ReconRefundBillOrderTask extends ReconBillOrderTask {
		public ReconRefundBillOrderTask(ClosableTimer closableTimer, String strReconStartDay, String strReconEndDay) {
			super(closableTimer, strReconStartDay, strReconEndDay);
		}
		
		@Override
		public Map<String, Map<String, String>> getNobMoreThanWxOrderRecord(String strReconStartDay,
				String strReconEndDay) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取Nob侧存在，而Tencent不存在的对账单基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select a.out_refund_no out_refund_no, a.refund_success_time refund_success_time, a.refund_fee refund_fee from tbl_refund_order a "
								+ " where a.out_refund_no not in (select b.out_refund_no from tbl_wx_refund_bill_info b where date_format(b.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
										+ " and date_format(b.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
										+ " and b.refund_status='" + RefundOrderEntity.SUCCESS + "') "
								+ " and date_format(a.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(a.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and a.refund_status='" + RefundOrderEntity.SUCCESS + "';";
				
				System.out.println(">>>--strSql = " + strSql);
				
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutRefundNo = rs.getString("out_refund_no");
					if (strOutRefundNo != null && !"".equals(strOutRefundNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutRefundNo);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							String strSrcRefundSuccTime = rs.getString("refund_success_time");
							Date srcDate = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss", strSrcRefundSuccTime);
							String strFormatedDate = CommonTool.getFormatDateStr(srcDate, "yyyyMMddHHmmss");
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_SUCCESS_TIME, strFormatedDate);
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_FEE, rs.getString("refund_fee"));
							mapOuterInfo.put(strOutRefundNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				// 补充子商户号
				Map<String, String> mapArgs = new HashMap<String, String>();
				String strSubMchId = null;
				
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				for (String strOutRefundNo : strKeys) {
					mapArgs.clear();
					mapArgs.put("out_refund_no", strOutRefundNo);
					String strOutTradeNo = getTblFieldValue("out_trade_no", "tbl_trans_order_refund_order", mapArgs);
					
					mapArgs.clear();
					mapArgs.put("out_trade_no", strOutTradeNo);
					strSubMchId = getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
					
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.SUB_MCH_ID, strSubMchId);
				}
				
				// 补充代理商ID
				String strAgentId = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// 补充随机立减费用
				for (String strOutRefundNo : strKeys) {
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.DISCOUNT_REFUND_FEE, "0");	// 随机立减优惠
				}
				
				// 补充腾讯侧手续费率、及手续费
				for (String strOutRefundNo : strKeys) {
					// 手续费率
					String strTempId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID);
					mapArgs.clear();
					mapArgs.put("id", strTempId);
					String strWxRate = getTblFieldValue("wechat_rate", "t_servant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.POUND_RATE, strWxRate);
					
					// 手续费
					String strRefundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.REFUND_FEE);
					String strPoundFee = getPoundFeeBaseOnRate(strRefundFee, strWxRate, 0);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.SERVICE_POUND_FEE, strPoundFee);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutRefundNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_EXIST, "1");
					
					// NOB侧计算的待结算实际金额(扣除腾讯侧手续费后的金额)
					String strRefundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.REFUND_FEE);
					String strServPoundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SERVICE_POUND_FEE);
					String strNobActFee = CommonTool.formatDoubleToHalfUp(Double.parseDouble(strRefundFee) - Double.parseDouble(strServPoundFee), 0, 0);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_ACT_FEE, strNobActFee);
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_EXIST, "0");
					
					// Tencent实际费用
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_ACT_FEE, "0");
					
					// NOB比Tencent所少的金额
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_LESS_WX_FEE, String.valueOf(0 - Integer.parseInt(strNobActFee)));
					
					// 对账结果(1: 成功  0：失败)
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_RESULT, "0");
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.IS_TRANSF_SETTLE, "0");
				}
			} catch(SQLException se) {
				se.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			return mapOuterInfo;
		}

		@Override
		public Map<String, Map<String, String>> getWxMoreThanNobOrderRecord(String strReconStartDay,
				String strReconEndDay) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取Tencent侧存在，而NOB不存在的对账单基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select a.out_refund_no out_refund_no, a.sub_mch_id sub_mch_id, a.refund_success_time refund_success_time, "
								+ " a.refund_fee refund_fee, a.discount_refund_fee discount_refund_fee, a.service_pound_fee service_pound_fee, "
								+ " a.pound_rate pound_rate from tbl_wx_refund_bill_info a"
								+ " where a.out_refund_no not in (select b.out_refund_no from tbl_refund_order b where date_format(b.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
										+ " and date_format(b.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
										+ " and b.refund_status='" + RefundOrderEntity.SUCCESS + "') "
								+ " and date_format(a.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(a.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and a.refund_status='" + RefundOrderEntity.SUCCESS + "';";
				
				System.out.println(">>>++--strSql = " + strSql);
				
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutRefundNo = rs.getString("out_refund_no");
					if (strOutRefundNo != null && !"".equals(strOutRefundNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutRefundNo);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							mapInnerInfo.put(ReconRefundBillOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
							String strSrcRefundSuccTime = rs.getString("refund_success_time");
							Date srcDate = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss", strSrcRefundSuccTime);
							String strFormatedDate = CommonTool.getFormatDateStr(srcDate, "yyyyMMddHHmmss");
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_SUCCESS_TIME, strFormatedDate);
							String strRefundFee = CommonTool.formatNullStrToZero(rs.getString("refund_fee"));
							Double dblRefundFee = Double.parseDouble(strRefundFee) * 100D;	// 将腾讯侧记录的金额“元”转换为“分”。
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_FEE, CommonTool.formatDoubleToHalfUp(dblRefundFee, 0, 0));
							String strDiscountRefundFee = CommonTool.formatNullStrToZero(rs.getString("discount_refund_fee"));
							Double dblDiscountRefundFee = Double.parseDouble(strDiscountRefundFee) * 100D;	// 将腾讯侧记录的金额“元”转换为“分”。
							mapInnerInfo.put(ReconRefundBillOrderEntity.DISCOUNT_REFUND_FEE, CommonTool.formatDoubleToHalfUp(dblDiscountRefundFee, 0, 0));
							String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
							Double dblServPoundFee = Math.abs(Double.parseDouble(strServPoundFee) * 100D);	// 将腾讯侧记录的金额“元”转换为“分”。
																									// 另外，由于腾讯侧记录的“退款手续费”为【负数】，为了便于计算，此处统一修改为【正数】。
							mapInnerInfo.put(ReconRefundBillOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(dblServPoundFee, 0, 0));
							String strPoundRate = rs.getString("pound_rate");
							strPoundRate = CommonTool.formatNullStrToSpace(strPoundRate).replace("%", "");	// 去掉腾讯手续费的百分比(%)	
							mapInnerInfo.put(ReconRefundBillOrderEntity.POUND_RATE, strPoundRate);
							mapOuterInfo.put(strOutRefundNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				String strSubMchId = null;
				
				// 补充代理商ID
				String strAgentId = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutRefundNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_EXIST, "0");
					
					// NOB侧实际金额
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_ACT_FEE, "0");
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_EXIST, "1");
					
					// Tencent侧计算的待结算实际费用(扣除腾讯手续费后的金额，单位为：分)
					String strRefundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.REFUND_FEE);
					String strServicePoundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SERVICE_POUND_FEE);
					Double dblWxActFee = Double.parseDouble(strRefundFee) - Double.parseDouble(strServicePoundFee);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_ACT_FEE, CommonTool.formatDoubleToHalfUp(dblWxActFee, 0, 0));
					
					// NOB比Tencent所少的金额
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_LESS_WX_FEE, CommonTool.formatDoubleToHalfUp(dblWxActFee, 0, 0));
					
					// 对账结果(1: 成功  0：失败)
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_RESULT, "0");
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.IS_TRANSF_SETTLE, "0");
				}
			} catch(SQLException se) {
				se.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			return mapOuterInfo;
		}

		@Override
		public Map<String, Map<String, String>> getWxEqualNobOrderRecord(String strReconStartDay, String strReconEndDay) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取Tencent侧存在，而NOB不存在的对账单基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select a.out_refund_no out_refund_no, a.sub_mch_id sub_mch_id, a.refund_success_time refund_success_time, "
								+ " a.refund_fee refund_fee, a.discount_refund_fee discount_refund_fee, a.service_pound_fee service_pound_fee, "
								+ " a.pound_rate pound_rate from tbl_wx_refund_bill_info a, tbl_refund_order b "
								+ " where a.out_refund_no=b.out_refund_no and date_format(a.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(a.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and a.refund_status='" + RefundOrderEntity.SUCCESS + "' "
								+ " and date_format(b.refund_success_time, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(b.refund_success_time, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and b.refund_status='" + RefundOrderEntity.SUCCESS + "';";
				
				System.out.println("++>>>++--strSql = " + strSql);
				
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutRefundNo = rs.getString("out_refund_no");
					if (strOutRefundNo != null && !"".equals(strOutRefundNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutRefundNo);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							mapInnerInfo.put(ReconRefundBillOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
							String strSrcRefundSuccTime = rs.getString("refund_success_time");
							Date srcDate = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss", strSrcRefundSuccTime);
							String strFormatedDate = CommonTool.getFormatDateStr(srcDate, "yyyyMMddHHmmss");
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_SUCCESS_TIME, strFormatedDate);
							String strRefundFee = CommonTool.formatNullStrToZero(rs.getString("refund_fee"));
							Double dblRefundFee = Double.parseDouble(strRefundFee) * 100D;	// 将腾讯侧记录的金额，由单位“元”转换为“分”
							mapInnerInfo.put(ReconRefundBillOrderEntity.REFUND_FEE, CommonTool.formatDoubleToHalfUp(dblRefundFee, 0, 0));
							String strDiscountRefundFee = CommonTool.formatNullStrToZero(rs.getString("discount_refund_fee"));
							Double dblDiscountRefundFee = Double.parseDouble(strDiscountRefundFee) * 100D;	// 将腾讯侧记录的金额，由单位“元”转换为“分”
							mapInnerInfo.put(ReconRefundBillOrderEntity.DISCOUNT_REFUND_FEE, CommonTool.formatDoubleToHalfUp(dblDiscountRefundFee, 0, 0));
							String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
							Double dblServPoundFee = Math.abs(Double.parseDouble(strServPoundFee) * 100D);	// 将腾讯侧记录的金额，由单位“元”转换为“分”
																									// 另外，由于腾讯侧记录的“退款手续费”为【负数】，为了便于计算，此处统一修改为【正数】。
							mapInnerInfo.put(ReconRefundBillOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(dblServPoundFee, 0, 0));
							String strPoundRate = rs.getString("pound_rate");
							strPoundRate = CommonTool.formatNullStrToSpace(strPoundRate).replace("%", "");	// 去掉腾讯手续费的百分比(%)	
							mapInnerInfo.put(ReconRefundBillOrderEntity.POUND_RATE, strPoundRate);
							mapOuterInfo.put(strOutRefundNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				String strSubMchId = null;
				
				// 补充代理商ID
				String strAgentId = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutRefundNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutRefundNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_EXIST, "1");
					
					// NOB侧计算的扣除腾讯手续费后待结算的实际金额(单位为：分)
					mapArgs.clear();
					mapArgs.put("out_refund_no", strOutRefundNo);
					String strRefundFee = getTblFieldValue("refund_fee", "tbl_refund_order", mapArgs);
					
					mapArgs.clear();
					mapArgs.put("id", mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID));
					String strNobWxRate = getTblFieldValue("wechat_rate", "t_servant", mapArgs);	// Nob侧记录的腾讯端费率
					String strNobSetlPoundFee = getPoundFeeBaseOnRate(strRefundFee, strNobWxRate, 0);
					String strNobActFee = CommonTool.formatDoubleToHalfUp(Double.parseDouble(strRefundFee) - Double.parseDouble(strNobSetlPoundFee), 0, 0);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_ACT_FEE, strNobActFee);
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_EXIST, "1");
					
					// Tencent侧计算的扣除腾讯手续费后待结算的实际费用(单位为：分)
					String strWxRefundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.REFUND_FEE);
					String strServicePoundFee = mapOuterInfo.get(strOutRefundNo).get(ReconRefundBillOrderEntity.SERVICE_POUND_FEE);
					Double dblWxActFee = Double.parseDouble(strWxRefundFee) - Double.parseDouble(strServicePoundFee);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_WX_ACT_FEE, CommonTool.formatDoubleToHalfUp(dblWxActFee, 0, 0));
					
					// NOB比Tencent所少的金额
					Double dblNobLessWxFee = dblWxActFee - Double.parseDouble(strNobActFee);
					String strNobLessWxFee = CommonTool.formatDoubleToHalfUp(dblNobLessWxFee, 0, 0);
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_NOB_LESS_WX_FEE, strNobLessWxFee);
					
					// 对账结果(1: 成功  0：失败)
					String strRecRst = null;
					String strWxPoundRate = mapOuterInfo.get(strOutRefundNo).get(ReconPayBillOrderEntity.POUND_RATE);
					double dblWxPoundRate = Double.parseDouble(strWxPoundRate);
					double dblNobPoundRate = Double.parseDouble(strNobWxRate);
					System.out.println("strOutRefundNo = " + strOutRefundNo);
					System.out.println("dblWxPoundRate = " + dblWxPoundRate);
					System.out.println("dblNobPoundRate = " + dblNobPoundRate);
					if ("0".equals(CommonTool.formatNullStrToZero(strNobLessWxFee))	// NOB与Tencent的实际费用差额为0
							&& 	dblNobPoundRate == dblWxPoundRate	// 腾讯侧计算的腾讯手续费率与NOB侧模板定义的腾讯手续费率相等
							) {	
						strRecRst = "1";
					} else {
						strRecRst = "0";
					}
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.REC_RESULT, strRecRst);
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutRefundNo).put(ReconRefundBillOrderEntity.IS_TRANSF_SETTLE, "0");
				}
			} catch(SQLException se) {
				se.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			return mapOuterInfo;
		}

		@Override
		public void insertFullRecInfoToTbl(Map<String, Map<String, String>> mapOuterInfo) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;

			// 取出指定时间段内，对账结果表中对账成功的订单号
			List<String[]> listArgs = new ArrayList<String[]>();
			listArgs.add(new String[] {"rec_result", "=", "1"});	// 1：对账成功 0：对账失败
			listArgs.add(new String[] {"date_format(refund_success_time, '%Y%m%d')", ">=", strReconStartDay});
			listArgs.add(new String[] {"date_format(refund_success_time, '%Y%m%d')", "<=", strReconEndDay});
			List<String> lstOutRefundNoRecOk = getTblFieldValueList("out_refund_no", "tbl_refund_order_recon_result", listArgs);
			
			// 为了不再更新对账结果表内对账成功的记录，需要将传入该方法的数据集剔除这些成功的数据
			Map<String, Map<String, String>> mapNewOuterInfo = super.getNewOuterInfo(mapOuterInfo, lstOutRefundNoRecOk);
			
			try {
				String strSql = "replace into tbl_refund_order_recon_result(out_refund_no, sub_mch_id, agent_id, pound_fee_temp_id, "
								+ " refund_success_time, refund_fee, discount_refund_fee, service_pound_fee, pound_rate, rec_nob_exist, "
								+ " rec_nob_act_fee, rec_wx_exist, rec_wx_act_fee, rec_nob_less_wx_fee, rec_result, is_transf_settle) "
								+ " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
				prst = conn.prepareStatement(strSql);

				String[] strOutRefundNoS = mapNewOuterInfo.keySet().toArray(new String[0]);
				int iOutRefundNoSize = strOutRefundNoS.length; 
				for (int i = 0; i < iOutRefundNoSize; i++) {
					String strOutRefundNo = strOutRefundNoS[i];
					Map<String, String> mapInner = mapNewOuterInfo.get(strOutRefundNo);

					prst.setString(1, strOutRefundNo);
					prst.setString(2, mapInner.get(ReconRefundBillOrderEntity.SUB_MCH_ID));
					prst.setString(3, mapInner.get(ReconRefundBillOrderEntity.AGENT_ID));
					prst.setString(4, mapInner.get(ReconRefundBillOrderEntity.POUND_FEE_TEMP_ID));
					prst.setString(5, mapInner.get(ReconRefundBillOrderEntity.REFUND_SUCCESS_TIME));
					prst.setString(6, mapInner.get(ReconRefundBillOrderEntity.REFUND_FEE));
					prst.setString(7, mapInner.get(ReconRefundBillOrderEntity.DISCOUNT_REFUND_FEE));
					prst.setString(8, mapInner.get(ReconRefundBillOrderEntity.SERVICE_POUND_FEE));
					prst.setString(9, mapInner.get(ReconRefundBillOrderEntity.POUND_RATE));
					prst.setString(10, mapInner.get(ReconRefundBillOrderEntity.REC_NOB_EXIST));
					prst.setString(11, mapInner.get(ReconRefundBillOrderEntity.REC_NOB_ACT_FEE));
					prst.setString(12, mapInner.get(ReconRefundBillOrderEntity.REC_WX_EXIST));
					prst.setString(13, mapInner.get(ReconRefundBillOrderEntity.REC_WX_ACT_FEE));
					prst.setString(14, mapInner.get(ReconRefundBillOrderEntity.REC_NOB_LESS_WX_FEE));
					prst.setString(15, mapInner.get(ReconRefundBillOrderEntity.REC_RESULT));
					prst.setString(16, mapInner.get(ReconRefundBillOrderEntity.IS_TRANSF_SETTLE));

					prst.addBatch();

					// 每10000条记录执行一批入库操作
					if ((i + 1) % 10000 == 0) {
						prst.executeBatch();
						conn.commit();
						prst.clearBatch();
					}
				}
				// 提交最后一批入库
				prst.executeBatch();
				conn.commit();

			} catch (SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
		}

		/**
		 * 校验当前退款单所属子商户是否配置相应的模板。
		 * @param strOrderNo
		 * @return
		 */
		@Override
		public boolean validRefTemplet(String strOrderNo) {
			boolean blnRefTemplet = false;
			Map<String, String> mapArgs = new HashMap<String, String>();
			mapArgs.put("out_refund_no", strOrderNo);
			String strOutTradeNo = getTblFieldValue("out_trade_no", "tbl_trans_order_refund_order", mapArgs);
			if (strOutTradeNo != null && !"".equals(strOutTradeNo)) {
				mapArgs.clear();
				mapArgs.put("out_trade_no", strOutTradeNo);
				String strSubMerchId = getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
				if (strSubMerchId != null && !"".equals(strSubMerchId)) {
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMerchId);
					String strTempletId = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					if (strTempletId != null && !"".equals(strTempletId)) {
						mapArgs.clear();
						mapArgs.put("id", strTempletId);
						String strServantCode = getTblFieldValue("servant_code", "t_servant", mapArgs);
						if (strServantCode != null && !"".equals(strServantCode)) {
							blnRefTemplet = true;
						}
					}
				}
			}
			
			return blnRefTemplet;
		}
	}
}
