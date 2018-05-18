package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.GenPaySettleOrderEntity;

public class GenPaySettleOrderServlet extends GenSettleOrderServlet {

	@Override
	public ProcSettleOrderTask getProcSettleOrderTask(ClosableTimer closableTimer) {
		return new ProcPaySettleOrderTask(closableTimer);
	}

	public class ProcPaySettleOrderTask extends ProcSettleOrderTask {
		public ProcPaySettleOrderTask(ClosableTimer closableTimer) {
			super(closableTimer);
		}

		@Override
		public Map<String, Map<String, String>> getMiddleSettleInfo() {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			Map<String, Map<String, String>> mapOuterInfo = new HashMap<String, Map<String, String>>();
			try {
				/** 获取中间结算单的基础信息 **/
				Map<String, String> mapInnerInfo = null;
				
				String strSql = "select out_trade_no, sub_mch_id, agent_id, trans_time_end, pound_fee_temp_id, total_fee, discount_fee, "
								+ " service_pound_fee from tbl_pay_order_recon_result where rec_result='1' and is_transf_settle='0'";
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					mapInnerInfo = new HashMap<String, String>();
					mapInnerInfo.put(GenPaySettleOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
					mapInnerInfo.put(GenPaySettleOrderEntity.AGENT_ID, rs.getString("agent_id"));
					mapInnerInfo.put(GenPaySettleOrderEntity.TIME_END, rs.getString("trans_time_end"));
					mapInnerInfo.put(GenPaySettleOrderEntity.POUND_FEE_TEMP_ID, rs.getString("pound_fee_temp_id"));
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strTotalFee = CommonTool.formatNullStrToZero(rs.getString("total_fee"));
					mapInnerInfo.put(GenPaySettleOrderEntity.TOTAL_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strTotalFee), 2, 2)); 
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strDiscountFee = CommonTool.formatNullStrToZero(rs.getString("discount_fee"));
					mapInnerInfo.put(GenPaySettleOrderEntity.DISCOUNT_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strDiscountFee), 2, 2));
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
					mapInnerInfo.put(GenPaySettleOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strServPoundFee), 2, 2));
					mapInnerInfo.put(GenPaySettleOrderEntity.SETTLEMENT_FEE_TYPE, "USD");
					mapOuterInfo.put(rs.getString("out_trade_no"), mapInnerInfo);
				}
				
				/** 补充中间结算单的其它信息 **/
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strOutTradeNoS = mapOuterInfo.keySet().toArray(new String[0]);
				for (String strOutTradeNo : strOutTradeNoS) {
					String strPoundFeeTempId = mapOuterInfo.get(strOutTradeNo).get(GenPaySettleOrderEntity.POUND_FEE_TEMP_ID);
					String strTotalFee = mapOuterInfo.get(strOutTradeNo).get(GenPaySettleOrderEntity.TOTAL_FEE);
					
					// 补充NOB手续费、结算状态、结算单ID
					mapArgs.clear();
					mapArgs.put("id", strPoundFeeTempId);
					String strNobRate = getTblFieldValue("bank_rate", "t_servant", mapArgs);
					String strNobPoundFee = getPoundFeeBaseOnRate(strTotalFee, strNobRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																								// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.NOB_POUND_FEE, strNobPoundFee);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.NOB_SETTLE_STATUS, GenPaySettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.NOB_SETTLE_ID, "");
					
					// 补充Harvest手续费、结算状态、结算单ID
					String strHarRate = getTblFieldValue("meiwei_rate", "t_servant", mapArgs);
					String strHarPoundFee = getPoundFeeBaseOnRate(strTotalFee, strHarRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																								// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.HAR_POUND_FEE, strHarPoundFee);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.HAR_SETTLE_STATUS, GenPaySettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.HAR_SETTLE_ID, "");
					
					// 补充Agent手续费、结算状态、结算单ID
					String strAgenRate = getTblFieldValue("agent_rate", "t_servant", mapArgs);
					String strAgenPoundFee = getPoundFeeBaseOnRate(strTotalFee, strAgenRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																									// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.AGEN_POUND_FEE, strAgenPoundFee);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.AGEN_SETTLE_STATUS, GenPaySettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.AGEN_SETTLE_ID, "");
					
					// 补充终端商户的实际结算资金、结算状态、结算单ID
					String strServPoundFee = CommonTool.formatNullStrToZero(mapOuterInfo.get(strOutTradeNo).get(GenPaySettleOrderEntity.SERVICE_POUND_FEE));
					double dblSubmchSettleFee = Double.parseDouble(CommonTool.formatNullStrToZero(strTotalFee)) - Double.parseDouble(strServPoundFee)
												- Double.parseDouble(strNobPoundFee) - Double.parseDouble(strHarPoundFee) - Double.parseDouble(strAgenPoundFee);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.SUBMCH_SETTLE_FEE, CommonTool.formatDoubleToHalfUp(dblSubmchSettleFee, 2, 2));
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.SUBMCH_SETTLE_STATUS, GenPaySettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutTradeNo).put(GenPaySettleOrderEntity.SUBMCH_SETTLE_ID, "");
				}
			} catch (SQLException se) {
				se.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			return mapOuterInfo;
		}

		@Override
		public boolean insertMiddleSettleInfoToTbl(Map<String, Map<String, String>> mapMiddleSettleInfo) {
			boolean blnInsRst = false;
			
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				String strSql = "replace into tbl_pay_settle_detail_order(out_trade_no, sub_mch_id, agent_id, trans_time_end, pound_fee_temp_id, "
								+ " total_fee, discount_fee, wx_pound_fee, nob_pound_fee, nob_settle_status, nob_settle_order_id, har_pound_fee, "
								+ " har_settle_status, har_settle_order_id, agen_pound_fee, agen_settle_status, agen_settle_order_id, "
								+ " submch_settle_fee, submch_settle_status, submch_settle_order_id, settlementfee_type) "
								+ "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
				prst = conn.prepareStatement(strSql);
				
				String[] strOutTradeNoS = mapMiddleSettleInfo.keySet().toArray(new String[0]);
				int iOutTradeNoSize = strOutTradeNoS.length;
				for (int i = 0; i < iOutTradeNoSize; i++) {
					String strOutTradeNo = strOutTradeNoS[i];
					Map<String, String> mapInnerInfos = mapMiddleSettleInfo.get(strOutTradeNo);
					prst.setString(1, strOutTradeNo);
					prst.setString(2, mapInnerInfos.get(GenPaySettleOrderEntity.SUB_MCH_ID));
					prst.setString(3, mapInnerInfos.get(GenPaySettleOrderEntity.AGENT_ID));
					prst.setString(4, mapInnerInfos.get(GenPaySettleOrderEntity.TIME_END));
					prst.setString(5, mapInnerInfos.get(GenPaySettleOrderEntity.POUND_FEE_TEMP_ID));
					prst.setString(6, mapInnerInfos.get(GenPaySettleOrderEntity.TOTAL_FEE));
					prst.setString(7, mapInnerInfos.get(GenPaySettleOrderEntity.DISCOUNT_FEE));
					prst.setString(8, mapInnerInfos.get(GenPaySettleOrderEntity.SERVICE_POUND_FEE));
					prst.setString(9, mapInnerInfos.get(GenPaySettleOrderEntity.NOB_POUND_FEE));
					prst.setString(10, mapInnerInfos.get(GenPaySettleOrderEntity.NOB_SETTLE_STATUS));
					prst.setString(11, mapInnerInfos.get(GenPaySettleOrderEntity.NOB_SETTLE_ID));
					prst.setString(12, mapInnerInfos.get(GenPaySettleOrderEntity.HAR_POUND_FEE));
					prst.setString(13, mapInnerInfos.get(GenPaySettleOrderEntity.HAR_SETTLE_STATUS));
					prst.setString(14, mapInnerInfos.get(GenPaySettleOrderEntity.HAR_SETTLE_ID));
					prst.setString(15, mapInnerInfos.get(GenPaySettleOrderEntity.AGEN_POUND_FEE));
					prst.setString(16, mapInnerInfos.get(GenPaySettleOrderEntity.AGEN_SETTLE_STATUS));
					prst.setString(17, mapInnerInfos.get(GenPaySettleOrderEntity.AGEN_SETTLE_ID));
					prst.setString(18, mapInnerInfos.get(GenPaySettleOrderEntity.SUBMCH_SETTLE_FEE));
					prst.setString(19, mapInnerInfos.get(GenPaySettleOrderEntity.SUBMCH_SETTLE_STATUS));
					prst.setString(20, mapInnerInfos.get(GenPaySettleOrderEntity.SUBMCH_SETTLE_ID));
					prst.setString(21, mapInnerInfos.get(GenPaySettleOrderEntity.SETTLEMENT_FEE_TYPE));
					
					prst.addBatch();
				}
				
				// 提交最后一批入库
				prst.executeBatch();
				conn.commit();
				
				blnInsRst = true;
			} catch (SQLException e) {
				e.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
				blnInsRst = false;
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
			
			return blnInsRst;
		}

		@Override
		public void updateReconResultTransfStatus(Map<String, Map<String, String>> mapMiddleSettleInfo) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				String strSql = "update tbl_pay_order_recon_result set is_transf_settle='1' where out_trade_no=?;";
				prst = conn.prepareStatement(strSql);
				
				String[] strOutTradeNoS = mapMiddleSettleInfo.keySet().toArray(new String[0]);
				int iOutTradeSize = strOutTradeNoS.length;
				for (int i = 0; i < iOutTradeSize; i++) {
					String strOutTradeNo = strOutTradeNoS[i];
					prst.setString(1, strOutTradeNo);
					prst.addBatch();
				}
				
				// 提交最后一批入库
				prst.executeBatch();
				conn.commit();
				
			} catch(SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
		}
	}
}
