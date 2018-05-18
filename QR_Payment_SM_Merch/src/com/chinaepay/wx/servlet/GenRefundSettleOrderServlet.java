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
import com.chinaepay.wx.entity.GenRefundSettleOrderEntity;

public class GenRefundSettleOrderServlet extends GenSettleOrderServlet {

	@Override
	public ProcSettleOrderTask getProcSettleOrderTask(ClosableTimer closableTimer) {
		return new ProcRefundSettleOrderTask(closableTimer);
	}

	public class ProcRefundSettleOrderTask extends ProcSettleOrderTask {
		public ProcRefundSettleOrderTask(ClosableTimer closableTimer) {
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
				
				String strSql = "select out_refund_no, sub_mch_id, agent_id, refund_success_time, pound_fee_temp_id, refund_fee, "
								+ " discount_refund_fee, service_pound_fee "
								+ " from tbl_refund_order_recon_result "
								+ " where rec_result='1' and is_transf_settle='0'";
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					mapInnerInfo = new HashMap<String, String>();
					mapInnerInfo.put(GenRefundSettleOrderEntity.SUB_MCH_ID, rs.getString("sub_mch_id"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.AGENT_ID, rs.getString("agent_id"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.REFUND_SUCCESS_TIME, rs.getString("refund_success_time"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.POUND_FEE_TEMP_ID, rs.getString("pound_fee_temp_id"));
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strRefundFee = CommonTool.formatNullStrToZero(rs.getString("refund_fee"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.REFUND_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strRefundFee), 2, 2));
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strDiscountRefundFee = CommonTool.formatNullStrToZero(rs.getString("discount_refund_fee"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.DISCOUNT_REFUND_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strDiscountRefundFee), 2, 2));
					// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					String strServPoundFee = CommonTool.formatNullStrToZero(rs.getString("service_pound_fee"));
					mapInnerInfo.put(GenRefundSettleOrderEntity.SERVICE_POUND_FEE, CommonTool.formatDoubleToHalfUp(Double.parseDouble(strServPoundFee), 2, 2));
					mapInnerInfo.put(GenRefundSettleOrderEntity.SETTLEMENT_FEE_TYPE, "USD");
					mapOuterInfo.put(rs.getString("out_refund_no"), mapInnerInfo);
				}
				
				/** 补充中间结算单的其它信息 **/
				Map<String, String> mapArgs = new HashMap<String, String>();
				String[] strOutRefundNoS = mapOuterInfo.keySet().toArray(new String[0]);
				for (String strOutRefundNo : strOutRefundNoS) {
					String strPoundFeeTempId = mapOuterInfo.get(strOutRefundNo).get(GenRefundSettleOrderEntity.POUND_FEE_TEMP_ID);
					String strRefundFee = mapOuterInfo.get(strOutRefundNo).get(GenRefundSettleOrderEntity.REFUND_FEE);
					
					// 补充NOB手续费、结算状态、结算单ID
					mapArgs.clear();
					mapArgs.put("id", strPoundFeeTempId);
					String strNobRate = getTblFieldValue("bank_rate", "t_servant", mapArgs);
					String strNobPoundFee = getPoundFeeBaseOnRate(strRefundFee, strNobRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																								// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.NOB_POUND_FEE, strNobPoundFee);	
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.NOB_SETTLE_STATUS, GenRefundSettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutRefundNo).put(GenPaySettleOrderEntity.NOB_SETTLE_ID, "");
					
					// 补充Harvest手续费、结算状态、结算单ID
					String strHarRate = getTblFieldValue("meiwei_rate", "t_servant", mapArgs);
					String strHarPoundFee = getPoundFeeBaseOnRate(strRefundFee, strHarRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																								// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.HAR_POUND_FEE, strHarPoundFee);
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.HAR_SETTLE_STATUS, GenRefundSettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutRefundNo).put(GenPaySettleOrderEntity.HAR_SETTLE_ID, "");
					
					// 补充Agent手续费、结算状态、结算单ID
					String strAgenRate = getTblFieldValue("agent_rate", "t_servant", mapArgs);
					String strAgenPoundFee = getPoundFeeBaseOnRate(strRefundFee, strAgenRate, 2);	// 为了避免计算差错，将金额单位为“分”时，保留两位小数点。
																									// 如：1.45分，最后四舍五入后为1分；1.52分，最后四舍五入后为2分。
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.AGEN_POUND_FEE, strAgenPoundFee);
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.AGEN_SETTLE_STATUS, GenRefundSettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutRefundNo).put(GenPaySettleOrderEntity.AGEN_SETTLE_ID, "");
					
					// 补充终端商户的实际结算资金、结算状态、结算单ID
					String strServPoundFee = CommonTool.formatNullStrToZero(mapOuterInfo.get(strOutRefundNo).get(GenRefundSettleOrderEntity.SERVICE_POUND_FEE));
					double dblSubmchSettleFee = Double.parseDouble(CommonTool.formatNullStrToZero(strRefundFee)) - Double.parseDouble(strServPoundFee)
												- Double.parseDouble(strNobPoundFee) - Double.parseDouble(strHarPoundFee) - Double.parseDouble(strAgenPoundFee);
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.SUBMCH_SETTLE_FEE, CommonTool.formatDoubleToHalfUp(dblSubmchSettleFee, 2, 2));
					mapOuterInfo.get(strOutRefundNo).put(GenRefundSettleOrderEntity.SUBMCH_SETTLE_STATUS, GenRefundSettleOrderEntity.SETTLE_WAITING);
					mapOuterInfo.get(strOutRefundNo).put(GenPaySettleOrderEntity.SUBMCH_SETTLE_ID, "");
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
				String strSql = "replace into tbl_refund_settle_detail_order(out_refund_no, sub_mch_id, agent_id, refund_success_time, "
								+ " pound_fee_temp_id, refund_fee, discount_refund_fee, wx_pound_fee, nob_pound_fee, nob_settle_status, "
								+ " nob_settle_order_id, har_pound_fee, har_settle_status, har_settle_order_id, agen_pound_fee, agen_settle_status, "
								+ " agen_settle_order_id, submch_settle_fee, submch_settle_status, submch_settle_order_id, settlementfee_type) "
								+ " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
				prst = conn.prepareStatement(strSql);
				
				String[] strOutRefundNoS = mapMiddleSettleInfo.keySet().toArray(new String[0]);
				int iOutRefundNoSize = strOutRefundNoS.length;
				for (int i = 0; i < iOutRefundNoSize; i++) {
					String strOutRefundNo = strOutRefundNoS[i];
					Map<String, String> mapInnerInfos = mapMiddleSettleInfo.get(strOutRefundNo);
					prst.setString(1, strOutRefundNo);
					prst.setString(2, mapInnerInfos.get(GenRefundSettleOrderEntity.SUB_MCH_ID));
					prst.setString(3, mapInnerInfos.get(GenRefundSettleOrderEntity.AGENT_ID));
					prst.setString(4, mapInnerInfos.get(GenRefundSettleOrderEntity.REFUND_SUCCESS_TIME));
					prst.setString(5, mapInnerInfos.get(GenRefundSettleOrderEntity.POUND_FEE_TEMP_ID));
					prst.setString(6, mapInnerInfos.get(GenRefundSettleOrderEntity.REFUND_FEE));
					prst.setString(7, mapInnerInfos.get(GenRefundSettleOrderEntity.DISCOUNT_REFUND_FEE));
					prst.setString(8, mapInnerInfos.get(GenRefundSettleOrderEntity.SERVICE_POUND_FEE));
					prst.setString(9, mapInnerInfos.get(GenRefundSettleOrderEntity.NOB_POUND_FEE));
					prst.setString(10, mapInnerInfos.get(GenRefundSettleOrderEntity.NOB_SETTLE_STATUS));
					prst.setString(11, mapInnerInfos.get(GenRefundSettleOrderEntity.NOB_SETTLE_ID));
					prst.setString(12, mapInnerInfos.get(GenRefundSettleOrderEntity.HAR_POUND_FEE));
					prst.setString(13, mapInnerInfos.get(GenRefundSettleOrderEntity.HAR_SETTLE_STATUS));
					prst.setString(14, mapInnerInfos.get(GenRefundSettleOrderEntity.HAR_SETTLE_ID));
					prst.setString(15, mapInnerInfos.get(GenRefundSettleOrderEntity.AGEN_POUND_FEE));
					prst.setString(16, mapInnerInfos.get(GenRefundSettleOrderEntity.AGEN_SETTLE_STATUS));
					prst.setString(17, mapInnerInfos.get(GenRefundSettleOrderEntity.AGEN_SETTLE_ID));
					prst.setString(18, mapInnerInfos.get(GenRefundSettleOrderEntity.SUBMCH_SETTLE_FEE));
					prst.setString(19, mapInnerInfos.get(GenRefundSettleOrderEntity.SUBMCH_SETTLE_STATUS));
					prst.setString(20, mapInnerInfos.get(GenRefundSettleOrderEntity.SUBMCH_SETTLE_ID));
					prst.setString(21, mapInnerInfos.get(GenRefundSettleOrderEntity.SETTLEMENT_FEE_TYPE));
					
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
				String strSql = "update tbl_refund_order_recon_result set is_transf_settle='1' where out_refund_no=?;";
				prst = conn.prepareStatement(strSql);
				
				String[] strOutRefundNoS = mapMiddleSettleInfo.keySet().toArray(new String[0]);
				int iOutRefundSize = strOutRefundNoS.length;
				for (int i = 0; i < iOutRefundSize; i++) {
					String strOutRefundNo = strOutRefundNoS[i];
					prst.setString(1, strOutRefundNo);
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
