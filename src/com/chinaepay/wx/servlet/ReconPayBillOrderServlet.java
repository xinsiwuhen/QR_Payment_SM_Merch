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
import com.chinaepay.wx.entity.PayOrderEntiry;
import com.chinaepay.wx.entity.ReconPayBillOrderEntity;

public class ReconPayBillOrderServlet extends ReconBillOrderServlet {
	@Override
	public ReconBillOrderTask getReconBillOrderTask(ClosableTimer closableTimer, String strReconStartDay,
			String strReconEndDay) {
		return new ReconPayBillOrderTask(closableTimer, strReconStartDay, strReconEndDay);
	}
	
	
	public class ReconPayBillOrderTask extends ReconBillOrderTask {

		public ReconPayBillOrderTask(ClosableTimer closableTimer, String strReconStartDay, String strReconEndDay) {
			super(closableTimer, strReconStartDay, strReconEndDay);
		}

		@Override
		public Map<String, Map<String, String>> getNobMoreThanWxOrderRecord(String strReconStartDay, String strReconEndDay) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取Nob侧存在，而Tencent不存在的对账单基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select a.out_trade_no out_trade_no, a.time_end time_end, a.total_fee total_fee from tbl_trans_order a "
								+ " where a.out_trade_no not in (select b.out_trade_no from tbl_wx_pay_bill_info b where date_format(b.trans_time, '%Y%m%d')>='" 
										+ strReconStartDay + "' and date_format(b.trans_time, '%Y%m%d')<='" + strReconEndDay 
										+ "' and (b.trade_state='" + PayOrderEntiry.SUCCESS 
										+ "' or b.trade_state='" + PayOrderEntiry.REFUND 
										+ "' or b.trade_state='" + PayOrderEntiry.REVOKED 
										+ "')) "
								+ " and date_format(a.time_end, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(a.time_end, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and (a.trade_state='"
								+ PayOrderEntiry.SUCCESS
								+ "' or a.trade_state='"
								+ PayOrderEntiry.REFUND
								+ "' or a.trade_state='"
								+ PayOrderEntiry.REVOKED
								+ "');";
				
				System.out.println("###strSql = " + strSql);
				
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutTradeNo = rs.getString("out_trade_no");
					
					System.out.println("#-#strOutTradeNo = " + strOutTradeNo);
					if (strOutTradeNo != null && !"".equals(strOutTradeNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutTradeNo);
						System.out.println("#-#blnValRefTemp = " + blnValRefTemp);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							mapInnerInfo.put(ReconPayBillOrderEntity.TIME_END, rs.getString("time_end"));
							mapInnerInfo.put(ReconPayBillOrderEntity.TOTAL_FEE, rs.getString("total_fee"));
							mapInnerInfo.put(ReconPayBillOrderEntity.DISCOUNT_FEE, "0");	// 随机立减优惠
							mapOuterInfo.put(strOutTradeNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				// 补充子商户号
				Map<String, String> mapArgs = new HashMap<String, String>();
				String strSubMchId = null;
				
				System.out.println("###mapOuterInfo = " + mapOuterInfo);
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				System.out.println("###strKeys.size = " + strKeys.length);
				for (String strOutTradeNo : strKeys) {
					mapArgs.clear();
					mapArgs.put("out_trade_no", strOutTradeNo);
					strSubMchId = getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.SUB_MCH_ID, strSubMchId);
				}
				
				// 补充代理商ID
				String strAgentId = null;
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// 补充腾讯侧手续费率、及手续费
				for (String strOutTradeNo : strKeys) {
					// 手续费率
					String strTempId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID);
					System.out.println(">>**>strTempId = " + strTempId);
					mapArgs.clear();
					mapArgs.put("id", strTempId);
					String strWxRate = getTblFieldValue("wechat_rate", "t_servant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.POUND_RATE, strWxRate);
					
					// 手续费
					String strTotalFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.TOTAL_FEE);
					String strPoundFee = getPoundFeeBaseOnRate(strTotalFee, strWxRate, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.SERVICE_POUND_FEE, strPoundFee);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutTradeNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_EXIST, "1");
					
					// NOB侧计算的待结算实际金额(扣除腾讯侧手续费后的金额)
					String strTotalFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.TOTAL_FEE);
					String strServPoundFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SERVICE_POUND_FEE);
					String strNobActFee = CommonTool.formatDoubleToHalfUp(Double.parseDouble(strTotalFee) - Double.parseDouble(strServPoundFee), 0, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_ACT_FEE, strNobActFee);
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_EXIST, "0");
					
					// Tencent实际费用
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_ACT_FEE, "0");
					
					// NOB比Tencent所少的金额
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_LESS_WX_FEE, String.valueOf(0 - Integer.parseInt(strNobActFee)));
					
					// 对账结果(1: 成功  0：失败)
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_RESULT, "0");
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.IS_TRANSF_SETTLE, "0");
				}
			} catch(SQLException se) {
				se.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			System.out.println("+mapOuterInfo = " + mapOuterInfo);
			
			return mapOuterInfo;
		}

		@Override
		public Map<String, Map<String, String>> getWxMoreThanNobOrderRecord(String strReconStartDay, String strReconEndDay) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取Tencent侧存在，而NOB不存在的对账单基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select a.out_trade_no out_trade_no, a.sub_mch_id sub_mch_id, a.trans_time trans_time, a.total_fee total_fee, "
								+ " a.discount_fee discount_fee, a.service_pound_fee service_pound_fee, a.pound_rate pound_rate from tbl_wx_pay_bill_info a "
								+ " where a.out_trade_no not in (select b.out_trade_no from tbl_trans_order b where date_format(b.time_end, '%Y%m%d')>='" + strReconStartDay + "' "
										+ " and date_format(b.time_end, '%Y%m%d')<='" + strReconEndDay + "' and (b.trade_state='" + PayOrderEntiry.SUCCESS 
										+ "' or b.trade_state='" + PayOrderEntiry.REFUND 
										+ "' or b.trade_state='" + PayOrderEntiry.REVOKED 
										+ "')) "
								+ " and date_format(a.trans_time, '%Y%m%d')>='" + strReconStartDay + "' " 
								+ " and date_format(a.trans_time, '%Y%m%d')<='" + strReconEndDay + "' and (a.trade_state='"
								+ PayOrderEntiry.SUCCESS
								+ "' or a.trade_state='"
								+ PayOrderEntiry.REFUND
								+ "' or a.trade_state='"
								+ PayOrderEntiry.REVOKED
								+ "');";
				System.out.println(">>++strSql= " + strSql);
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutTradeNo = rs.getString("out_trade_no");
					if (strOutTradeNo != null && !"".equals(strOutTradeNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutTradeNo);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							mapInnerInfo.put(ReconPayBillOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
							String strSrcTransTimeEnd = rs.getString("trans_time");
							Date srcDate = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss", strSrcTransTimeEnd);
							String strFormatedDate = CommonTool.getFormatDateStr(srcDate, "yyyyMMddHHmmss");
							mapInnerInfo.put(ReconPayBillOrderEntity.TIME_END, strFormatedDate);
							String strTotalFee = CommonTool.formatNullStrToZero(rs.getString("total_fee"));
							Double dblTotalFee = Double.parseDouble(strTotalFee) * 100D;	// 将腾讯侧记录的金额“元”转换为“分”。
							mapInnerInfo.put(ReconPayBillOrderEntity.TOTAL_FEE, CommonTool.formatDoubleToHalfUp(dblTotalFee, 0, 0));
							String strDiscountFee = CommonTool.formatNullStrToZero(rs.getString("discount_fee"));
							Double dblDiscountFee = Double.parseDouble(strDiscountFee) * 100D;	// 将腾讯侧记录的金额“元”转换为“分”。
							mapInnerInfo.put(ReconPayBillOrderEntity.DISCOUNT_FEE, CommonTool.formatDoubleToHalfUp(dblDiscountFee, 0, 0));
							String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
							Double dblServPoundFee = Double.parseDouble(strServPoundFee) * 100D;	// 将腾讯侧记录的金额“元”转换为“分”。
							mapInnerInfo.put(ReconPayBillOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(dblServPoundFee, 0, 0));
							String strPoundRate = rs.getString("pound_rate");
							strPoundRate = CommonTool.formatNullStrToSpace(strPoundRate).replace("%", "");	// 去掉腾讯手续费的百分比(%)	
							mapInnerInfo.put(ReconPayBillOrderEntity.POUND_RATE, strPoundRate);
							mapOuterInfo.put(strOutTradeNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				// 补充代理商ID
				String strAgentId = null;
				String strSubMchId = null;
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutTradeNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_EXIST, "0");
					
					// NOB侧实际金额
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_ACT_FEE, "0");
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_EXIST, "1");
					
					// Tencent侧计算的待结算实际费用(扣除腾讯手续费后的金额，单位为：分)
					String strTotalFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.TOTAL_FEE);
					String strPoundFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SERVICE_POUND_FEE);
					Double dblWxActFee = Double.parseDouble(strTotalFee) - Double.parseDouble(strPoundFee);
					String strWxActFee = CommonTool.formatDoubleToHalfUp(dblWxActFee, 0, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_ACT_FEE, strWxActFee);
					
					// NOB比Tencent所少的金额
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_LESS_WX_FEE, strWxActFee);
					
					// 对账结果(1: 成功  0：失败)
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_RESULT, "0");
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.IS_TRANSF_SETTLE, "0");
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
				
				String strSql = "select a.out_trade_no out_trade_no, a.sub_mch_id sub_mch_id, a.trans_time trans_time, a.total_fee total_fee, "
								+ " a.discount_fee discount_fee, a.service_pound_fee service_pound_fee, a.pound_rate pound_rate "
								+ " from tbl_wx_pay_bill_info a, tbl_trans_order b where a.out_trade_no=b.out_trade_no "
								+ " and date_format(a.trans_time, '%Y%m%d')>='" + strReconStartDay  + "' "
								+ " and date_format(a.trans_time, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and (a.trade_state='" + PayOrderEntiry.SUCCESS + "' " 
								+ " or a.trade_state='" + PayOrderEntiry.REFUND  + "' "
								+ " or a.trade_state='" + PayOrderEntiry.REVOKED + "') "
								+ " and date_format(b.time_end, '%Y%m%d')>='" + strReconStartDay + "' "
								+ " and date_format(b.time_end, '%Y%m%d')<='" + strReconEndDay + "' "
								+ " and (b.trade_state='" + PayOrderEntiry.SUCCESS + "' " 
								+ " or b.trade_state='" + PayOrderEntiry.REFUND  + "' "
								+ " or b.trade_state='" + PayOrderEntiry.REVOKED + "');";
				
				System.out.println("++>>>>strSql= " + strSql);
				
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					// 校验当前订单是否配置分佣模板 
					String strOutTradeNo = rs.getString("out_trade_no");
					System.out.println("strOutTradeNo = " + strOutTradeNo);
					if (strOutTradeNo != null && !"".equals(strOutTradeNo)) {
						boolean blnValRefTemp = this.validRefTemplet(strOutTradeNo);
						System.out.println("blnValRefTemp = " + blnValRefTemp);
						if (blnValRefTemp) {
							mapInnerInfo = new HashMap<String, String>();
							mapInnerInfo.put(ReconPayBillOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
							String strSrcTransTimeEnd = rs.getString("trans_time");
							Date srcDate = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss", strSrcTransTimeEnd);
							String strFormatedDate = CommonTool.getFormatDateStr(srcDate, "yyyyMMddHHmmss");
							mapInnerInfo.put(ReconPayBillOrderEntity.TIME_END, strFormatedDate);
							String strTotalFee = CommonTool.formatNullStrToZero(rs.getString("total_fee"));
							Double dblTotalFee = Double.parseDouble(strTotalFee) * 100D;	// 将腾讯侧记录的金额，由单位“元”转换为“分”
							mapInnerInfo.put(ReconPayBillOrderEntity.TOTAL_FEE, CommonTool.formatDoubleToHalfUp(dblTotalFee, 0, 0));
							String strDiscountFee = CommonTool.formatNullStrToZero(rs.getString("discount_fee"));
							Double dblDiscountFee = Double.parseDouble(strDiscountFee) * 100D;	// 将腾讯侧记录的金额，由单位“元”转换为“分”
							mapInnerInfo.put(ReconPayBillOrderEntity.DISCOUNT_FEE, CommonTool.formatDoubleToHalfUp(dblDiscountFee, 0, 0));
							String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
							Double dblServPoundFee = Double.parseDouble(strServPoundFee) * 100D;	// 将腾讯侧记录的金额，由单位“元”转换为“分”
							mapInnerInfo.put(ReconPayBillOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(dblServPoundFee, 0, 0));
							String strPoundRate = rs.getString("pound_rate");
							strPoundRate = CommonTool.formatNullStrToSpace(strPoundRate).replace("%", "");	// 去掉腾讯手续费的百分比(%)	
							mapInnerInfo.put(ReconPayBillOrderEntity.POUND_RATE, strPoundRate);
							mapOuterInfo.put(strOutTradeNo, mapInnerInfo);
						}
					}
				}
				
				/** 补充关联表相关的信息 **/
				// 补充代理商ID
				String strAgentId = null;
				String strSubMchId = null;
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strKeys = mapOuterInfo.keySet().toArray(new String[0]);
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strAgentId = getTblFieldValue("agent_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.AGENT_ID, strAgentId);
				}
				
				// 补充模板ID
				String strTempletID = null;
				for (String strOutTradeNo : strKeys) {
					strSubMchId = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SUB_MCH_ID);
					mapArgs.clear();
					mapArgs.put("sub_merchant_code", strSubMchId);
					strTempletID = getTblFieldValue("servant_id", "t_merchant", mapArgs);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID, strTempletID);
				}
				
				// NOB(Harvest)侧是否存在、NOB侧实际金额、Tecent侧是否存在、Tencent实际费用、NOB比Tencent所少的金额、对账结果、是否被转移到结算单处理
				for (String strOutTradeNo : strKeys) {
					// NOB侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_EXIST, "1");
					
					// NOB侧计算的扣除腾讯手续费后待结算的实际金额(单位为：分)
					mapArgs.clear();
					mapArgs.put("out_trade_no", strOutTradeNo);
					String strNobTotalFee = CommonTool.formatNullStrToZero(getTblFieldValue("total_fee", "tbl_trans_order", mapArgs));	// Nob侧的TotalFee
					
					mapArgs.clear();
					mapArgs.put("id", mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID));
					String strNobWxRate = CommonTool.formatNullStrToZero(getTblFieldValue("wechat_rate", "t_servant", mapArgs));	// Nob侧记录的腾讯端费率
					String strNobSetlPoundFee = getPoundFeeBaseOnRate(strNobTotalFee, strNobWxRate, 0);
					String strNobActFee = CommonTool.formatDoubleToHalfUp(Double.parseDouble(strNobTotalFee) - Double.parseDouble(strNobSetlPoundFee), 0, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_ACT_FEE, strNobActFee);
					
					// Tecent侧是否存在
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_EXIST, "1");
					
					// Tencent侧计算的扣除腾讯手续费后待结算的实际费用(单位为：分)
					String strTotalFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.TOTAL_FEE);
					String strPoundFee = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.SERVICE_POUND_FEE);
					Double dblWxActFee = Double.parseDouble(strTotalFee) - Double.parseDouble(strPoundFee);
					String strWxActFee = CommonTool.formatDoubleToHalfUp(dblWxActFee, 0, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_WX_ACT_FEE, strWxActFee);
					
					// NOB比Tencent所少的金额
					Double dblNobLessWxFee = Double.parseDouble(strWxActFee) - Double.parseDouble(strNobActFee);
					String strNobLessWxFee = CommonTool.formatDoubleToHalfUp(dblNobLessWxFee, 0, 0);
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_NOB_LESS_WX_FEE, strNobLessWxFee);
					
					// 对账结果(1: 成功  0：失败)
					String strRecRst = null;
					String strWxPoundRate = mapOuterInfo.get(strOutTradeNo).get(ReconPayBillOrderEntity.POUND_RATE);
					double dblWxPoundRate = Double.parseDouble(strWxPoundRate);
					double dblNobPoundRate = Double.parseDouble(strNobWxRate);
					System.out.println("strOutTradeNo = " + strOutTradeNo);
					System.out.println("dblWxPoundRate = " + dblWxPoundRate);
					System.out.println("dblNobPoundRate = " + dblNobPoundRate);
					if ("0".equals(CommonTool.formatNullStrToZero(strNobLessWxFee))	// NOB与Tencent的实际费用差额为0
							&& 	dblNobPoundRate == dblWxPoundRate	// 腾讯侧计算的腾讯手续费率与NOB侧模板定义的腾讯手续费率相等
							) {	
						strRecRst = "1";
					} else {
						strRecRst = "0";
					}
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.REC_RESULT, strRecRst);
					
					// 是否被转移到结算单处理
					mapOuterInfo.get(strOutTradeNo).put(ReconPayBillOrderEntity.IS_TRANSF_SETTLE, "0");
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
			listArgs.add(new String[] {"date_format(trans_time_end, '%Y%m%d')", ">=", strReconStartDay});
			listArgs.add(new String[] {"date_format(trans_time_end, '%Y%m%d')", "<=", strReconEndDay});
			List<String> lstOutTradeNoRecOk = getTblFieldValueList("out_trade_no", "tbl_pay_order_recon_result", listArgs);
			
			// 为了不再更新对账结果表内对账成功的记录，需要将传入该方法的数据集剔除这些成功的数据
			Map<String, Map<String, String>> mapNewOuterInfo = super.getNewOuterInfo(mapOuterInfo, lstOutTradeNoRecOk);
			
			try {
				String strSql = "replace into tbl_pay_order_recon_result(out_trade_no, sub_mch_id, agent_id, pound_fee_temp_id, "
								+ " trans_time_end, total_fee, discount_fee, service_pound_fee, pound_rate, rec_nob_exist, "
								+ " rec_nob_act_fee, rec_wx_exist, rec_wx_act_fee, rec_nob_less_wx_fee, rec_result, is_transf_settle) "
								+ " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
				
				System.out.println("---strSql = " + strSql);
				
				prst = conn.prepareStatement(strSql);

				String[] strOutTradeNoS = mapNewOuterInfo.keySet().toArray(new String[0]);
				int iOutTradeNoSize = strOutTradeNoS.length;
				for (int i = 0; i < iOutTradeNoSize; i++) {
					String strOutTradeNo = strOutTradeNoS[i];
					Map<String, String> mapInner = mapNewOuterInfo.get(strOutTradeNo);

					prst.setString(1, strOutTradeNo);
					prst.setString(2, mapInner.get(ReconPayBillOrderEntity.SUB_MCH_ID));
					prst.setString(3, mapInner.get(ReconPayBillOrderEntity.AGENT_ID));
					prst.setString(4, mapInner.get(ReconPayBillOrderEntity.POUND_FEE_TEMP_ID));
					prst.setString(5, mapInner.get(ReconPayBillOrderEntity.TIME_END));
					prst.setString(6, mapInner.get(ReconPayBillOrderEntity.TOTAL_FEE));
					prst.setString(7, mapInner.get(ReconPayBillOrderEntity.DISCOUNT_FEE));
					prst.setString(8, mapInner.get(ReconPayBillOrderEntity.SERVICE_POUND_FEE));
					prst.setString(9, mapInner.get(ReconPayBillOrderEntity.POUND_RATE));
					prst.setString(10, mapInner.get(ReconPayBillOrderEntity.REC_NOB_EXIST));
					prst.setString(11, mapInner.get(ReconPayBillOrderEntity.REC_NOB_ACT_FEE));
					prst.setString(12, mapInner.get(ReconPayBillOrderEntity.REC_WX_EXIST));
					prst.setString(13, mapInner.get(ReconPayBillOrderEntity.REC_WX_ACT_FEE));
					prst.setString(14, mapInner.get(ReconPayBillOrderEntity.REC_NOB_LESS_WX_FEE));
					prst.setString(15, mapInner.get(ReconPayBillOrderEntity.REC_RESULT));
					prst.setString(16, mapInner.get(ReconPayBillOrderEntity.IS_TRANSF_SETTLE));

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
		 * 校验当前支付单所属子商户是否配置相应的模板。
		 * @param strOrderNo
		 * @return
		 */
		@Override
		public boolean validRefTemplet(String strOrderNo) {
			boolean blnRefTemplet = false;
			Map<String, String> mapArgs = new HashMap<String, String>();
			mapArgs.put("out_trade_no", strOrderNo);
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
			return blnRefTemplet;
		}
	}
}
