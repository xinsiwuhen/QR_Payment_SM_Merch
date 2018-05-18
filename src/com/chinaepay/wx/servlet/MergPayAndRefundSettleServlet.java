package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.common.JsonRespObj;
import com.chinaepay.wx.entity.MergPayAndRefundSettleEntity;

import net.sf.json.JSONObject;

public class MergPayAndRefundSettleServlet extends ProcBillAndSettleOrderServlet {
	public void init() {
		try {
			super.init();
			
			String strHour = this.getServletContext().getInitParameter("Hour_ProcFinalSettle");
			String strMinute = this.getServletContext().getInitParameter("Minute_ProcFinalSettle");
			String strSecond = this.getServletContext().getInitParameter("Second_ProcFinalSettle");
			String strDelayTime = this.getServletContext().getInitParameter("DelayTime_ProcFinalSettle");
	
			MergPayAndRefundSettleThread mparst = new MergPayAndRefundSettleThread(strHour, strMinute, strSecond, strDelayTime);
			mparst.start();
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		ClosableTimer closableTimer = new ClosableTimer(true);	// 执行完任务后关闭Timer
		TimerTask task = new ProcFinalSettleOrderTask(closableTimer);
        closableTimer.schedule(task, 0L);
        
        JsonRespObj respObj = new JsonRespObj();
		String strProResult = "1";
		String strReturnMsg = "依据模板计算最终的结算单处理任务，已经提交后台执行！";
        respObj.setRespCode(strProResult);
		respObj.setRespMsg(strReturnMsg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	public class MergPayAndRefundSettleThread extends Thread {
		private String strHour = null;
		private String strMinute = null;
		private String strSecond = null;
		private String strDelayTime = null;
		
		public MergPayAndRefundSettleThread(String strHour, String strMinute, String strSecond, String strDelayTime) {
			this.strHour = strHour;
			this.strMinute = strMinute;
			this.strSecond = strSecond;
			this.strDelayTime = strDelayTime;
		}
		
		public void run() {
			// 当前时间
			Date nowDate =  new Date();
			long lngNowMillSec = nowDate.getTime();
			
			// 获取指定日历参数的时间
			Date defineDate = getFixDateBasedOnArgs(strHour, strMinute, strSecond);
			long lngDefMillSec = defineDate.getTime();
			
			ClosableTimer closableTimer = null;
			TimerTask task = null;
			if (lngNowMillSec < lngDefMillSec) {
				closableTimer = new ClosableTimer(false);
		        task = new ProcFinalSettleOrderTask(closableTimer);
		        closableTimer.scheduleAtFixedRate(task, defineDate, CommonInfo.ONE_DAY_TIME);
			}
			else {
				// 执行一次任务(依据抓取数据的时间)，并在任务结束后关闭
				closableTimer = new ClosableTimer(true);
				task = new ProcFinalSettleOrderTask(closableTimer);
				closableTimer.schedule(task, Long.parseLong(strDelayTime) * 60 * 1000);
				
				// 在次日18点开始执行一次任务， 以后每隔24小时的轮询周期执行相同任务
				closableTimer = new ClosableTimer(false);
				task = new ProcFinalSettleOrderTask(closableTimer);
				Date nextDay = CommonTool.getBefOrAftDate(defineDate, CommonInfo.ONE_DAY_TIME);
				closableTimer.scheduleAtFixedRate(task, nextDay, CommonInfo.ONE_DAY_TIME);
			}
		}
	}
	
	
	public class ProcFinalSettleOrderTask extends TimerTask {
		private ClosableTimer closableTimer = null;
		
		public ProcFinalSettleOrderTask(ClosableTimer closableTimer) {
			super();
			this.closableTimer = closableTimer;
		}
		
		public void run() {
			/** 将中间结算单(含：支付/退款)信息，按各机构维度插入到最终结算时的临时表 **/
			// 清空中间结算单的临时表
			this.clearMidleSettleTbl();
			// 将【支付单对应的中间结算单】信息插入到临时表
			this.inserPayMidleSettleList();
			// 将【退款单对应的中间结算单】信息插入到临时表
			this.inserRefundMidleSettleList();	// 【注意】：方法内已经将退款单的金额取负数
			
			/** 生成各机构的最终结算单信息、将其更新到最终结算单表、更新中间结算单表的各机构结算状态 **/
			// 生成各机构的最终结算单信息
			List<String[]> listFinalSettleInfo = this.getFinalSettleInfo();
			
			// 更新到最终结算单表, 以及更新中间结算单表的各机构结算状态
			Connection conn = null;
			try {
				conn = MysqlConnectionPool.getInstance().getConnection(false);
				
				// 更新到最终结算单表
				List<String[]> lstOuterInfo = this.insertFinalSettleInfo(listFinalSettleInfo, conn);
				
				// 更新临时表内的结算单号、以及结算状态(需结算金额为0的结算单，结算状态直接更新为：已结算)
				this.updateTempSettleOrderNo(lstOuterInfo, conn);
				
				/* 更新中间结算单表的各机构结算状态 */
				// 更新中间结算单表状态前，获取订单号、订单类型、最终结算单ID
				List<String[]> listMidlSetlInfo = this.getMidlSetlInfo(conn);
				System.out.println("listMidlSetlInfo.size = " + listMidlSetlInfo.size());
				
				// 更新[支付单、退款单]结算表中各机构的状态、以及最终结算单号
				this.updatePayAndRefDetailInfo(listMidlSetlInfo, conn);
				
				// 提交所有相关表的数据，保持事务一致性
				conn.commit();
			} catch (SQLException e) {
				e.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(conn);
			}
			
			// 所有业务做完后，再次清空中间结算单的临时表
			this.clearMidleSettleTbl();
			
			/** 判断是否需要关闭任务时钟 **/
			if (closableTimer.isNeedClose()) {
				closableTimer.cancel();
			}
		}
		
		/**
		 * 清空中间结算单的临时表。
		 */
		private void clearMidleSettleTbl() {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			String strSql = "delete from tbl_temp_settle_order;";
			System.out.println("++$+strSql = " + strSql);
			PreparedStatement prst = null;
			try {
				prst = conn.prepareStatement(strSql);
				prst.execute();
				conn.commit();
			} catch (SQLException e) {
				e.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
		}
		
		/**
		 * 将【支付单对应的中间结算单】信息插入到临时表。
		 */
		private void inserPayMidleSettleList() {
			// 获取NOB数据
			String strSql = "select '" + CommonInfo.NOB_MCH_ID + "' org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_NOB + "' org_type, "
							+ " out_trade_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_PAYMENT + "' order_type, "
							+ " date_format(trans_time_end, '%Y%m%d') settle_belong_date, nob_pound_fee settle_fee_amount "
							+ " from tbl_pay_settle_detail_order where nob_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listNobTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("++listNobTempInfo = " + listNobTempInfo);
			this.insertOrgTempOrder(listNobTempInfo);
			
			// 获取Harvest数据
			strSql = "select '" + CommonInfo.HARVEST_ID + "' org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_HARVEST + "' org_type, "
							+ " out_trade_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_PAYMENT + "' order_type, "
							+ " date_format(trans_time_end, '%Y%m%d') settle_belong_date, har_pound_fee settle_fee_amount "
							+ " from tbl_pay_settle_detail_order where har_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listHarTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("++listHarTempInfo = " + listHarTempInfo);
			this.insertOrgTempOrder(listHarTempInfo);
			 
			// 获取Agent数据
			strSql = "select agent_id org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_AGENT + "' org_type, "
							+ " out_trade_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_PAYMENT + "' order_type, "
							+ " date_format(trans_time_end, '%Y%m%d') settle_belong_date, agen_pound_fee settle_fee_amount "
							+ " from tbl_pay_settle_detail_order where agen_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listAgenTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("++listAgenTempInfo = " + listAgenTempInfo);
			this.insertOrgTempOrder(listAgenTempInfo);
			
			// 获取SubMch数据
			strSql = "select sub_mch_id org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_SUB_MCH + "' org_type, "
							+ " out_trade_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_PAYMENT + "' order_type, "
							+ " date_format(trans_time_end, '%Y%m%d') settle_belong_date, submch_settle_fee settle_fee_amount "
							+ " from tbl_pay_settle_detail_order where submch_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listSubmchTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("++listSubmchTempInfo = " + listSubmchTempInfo);
			this.insertOrgTempOrder(listSubmchTempInfo);
		}
		
		/**
		 * 将【退款单对应的中间结算单】信息插入到临时表。
		 */
		private void inserRefundMidleSettleList() {
			// 获取NOB数据
			String strSql = "select '" + CommonInfo.NOB_MCH_ID + "' org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_NOB + "' org_type, "
							+ " out_refund_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_REFUND + "' order_type, "
							+ " date_format(refund_success_time, '%Y%m%d') settle_belong_date, 0 - nob_pound_fee settle_fee_amount "
							+ " from tbl_refund_settle_detail_order where nob_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listNobTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("##listNobTempInfo = " + listNobTempInfo);
			this.insertOrgTempOrder(listNobTempInfo);
			
			// 获取Harvest数据
			strSql = "select '" + CommonInfo.HARVEST_ID + "' org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_HARVEST + "' org_type, "
							+ " out_refund_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_REFUND + "' order_type, "
							+ " date_format(refund_success_time, '%Y%m%d') settle_belong_date, 0 - har_pound_fee settle_fee_amount "
							+ " from tbl_refund_settle_detail_order where har_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listHarTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("##listHarTempInfo = " + listHarTempInfo);
			this.insertOrgTempOrder(listHarTempInfo);
			 
			// 获取Agent数据
			strSql = "select agent_id org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_AGENT + "' org_type, "
							+ " out_refund_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_REFUND + "' order_type, "
							+ " date_format(refund_success_time, '%Y%m%d') settle_belong_date, 0 - agen_pound_fee settle_fee_amount "
							+ " from tbl_refund_settle_detail_order where agen_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listAgenTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("##listAgenTempInfo = " + listAgenTempInfo);
			this.insertOrgTempOrder(listAgenTempInfo);
			
			// 获取SubMch数据
			strSql = "select sub_mch_id org_id, '" + MergPayAndRefundSettleEntity.ORG_TYPE_SUB_MCH + "' org_type, "
							+ " out_refund_no out_order_no, '" + MergPayAndRefundSettleEntity.ORDER_TYPE_REFUND + "' order_type, "
							+ " date_format(refund_success_time, '%Y%m%d') settle_belong_date, 0 - submch_settle_fee settle_fee_amount "
							+ " from tbl_refund_settle_detail_order where submch_settle_status in ('" 
							+ MergPayAndRefundSettleEntity.SETTLE_WAITING + "', '" + MergPayAndRefundSettleEntity.SETTLE_FAIL + "');";
			List<String[]> listSubmchTempInfo = this.getOrgTempOrder(strSql);
			System.out.println("##listSubmchTempInfo = " + listSubmchTempInfo);
			this.insertOrgTempOrder(listSubmchTempInfo);
		}
		
		/**
		 * 生成各机构的最终结算单信息。
		 * @return
		 */
		private List<String[]> getFinalSettleInfo() {
			List<String[]> listFinalSettleInfo = new ArrayList<String[]>();
			
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			try {
				String strSql = "select org_id, org_type, settle_belong_date, sum(settle_fee_amount) settle_fee_amount from tbl_temp_settle_order "
								+ " group by org_id, settle_belong_date, org_type order by org_id, settle_belong_date;";
				System.out.println("###strSql = " + strSql);
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					String strSetlOrderId = CommonTool.getRandomString(32);
					String strOrgId = rs.getString("org_id");
					String strOrgType = rs.getString("org_type");
					String strSetlBelongDate = rs.getString("settle_belong_date");
					String strSetlFeeAmount = rs.getString("settle_fee_amount");
					String[] strFinalSetInfo = new String[] {strSetlOrderId, strOrgId, strOrgType, strSetlBelongDate, strSetlFeeAmount};
					listFinalSettleInfo.add(strFinalSetInfo);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			return listFinalSettleInfo;
		}
		
		/**
		 * 更新到最终结算单表。
		 * @param listFinalSettleInfo
		 * @throws SQLException 
		 */
		private List<String[]> insertFinalSettleInfo(List<String[]> listFinalSettleInfo, Connection conn) throws SQLException  {
			List<String[]> lstOuterInfo = new ArrayList<String[]>();
			
			String strSql = "replace into tbl_settlement_sum_order(settle_order_id, org_id, org_type, settle_belong_date, settle_batch_no, "
							+ " settle_start_time, settle_end_time, settle_fee_amount, settle_fee_type, settle_status) "
							+ " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
			PreparedStatement prst = conn.prepareStatement(strSql);
			
			for (int i = 0; i < listFinalSettleInfo.size(); i++) {
				String[] strFinalSetlInfo = listFinalSettleInfo.get(i);
				String strSetlOrderId = strFinalSetlInfo[0];
				String strOrgId = strFinalSetlInfo[1];
				String strOrgType = strFinalSetlInfo[2];
				String strSetlBelongDate = strFinalSetlInfo[3];
				String strSetlBatchNo = this.getPlusedBatchNo(strOrgId, strSetlBelongDate);
				String strSetlStartTime = CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss");
				String strSetlEndTime = "";
				String strSetlFeeAmount = strFinalSetlInfo[4];
				System.out.println("strSetlFeeAmount = " + strSetlFeeAmount);

				String strSetlFeeType = "USD";
				String strSetlStatus = null;
				// 将结算金额>0(需给下级结算)、结算金额<0(为子商户垫过款)的记录入库
				// 结算单内将金额单位由“分”转换成“元”
				String strUSDYuan = CommonTool.formatCentToYuan(strSetlFeeAmount, 2);
				double dblUSDYuan = Double.parseDouble(strUSDYuan);
				if (dblUSDYuan == 0.00d) {
					strSetlStatus = MergPayAndRefundSettleEntity.SETTLE_SUCESS;	// 结算金额为0时，结算状态为“已结算”
					strUSDYuan = String.valueOf(CommonTool.formatDoubleToHalfUp(Math.abs(dblUSDYuan), 2, 2));
					strSetlEndTime = CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss");
				} else {
					strSetlStatus = MergPayAndRefundSettleEntity.SETTLE_PROCESSING;	// 结算金额不为0时，结算状态为“结算中”
				}
				prst.setString(1, strSetlOrderId);
				prst.setString(2, strOrgId);
				prst.setString(3, strOrgType);
				prst.setString(4, strSetlBelongDate);
				prst.setString(5, strSetlBatchNo);
				prst.setString(6, strSetlStartTime);
				prst.setString(7, strSetlEndTime);
				prst.setString(8, strUSDYuan);
				prst.setString(9, strSetlFeeType);
				prst.setString(10, strSetlStatus);
				
				prst.addBatch();
				
				/** 生成更新临时表结算单号、结算单状态相关的集合 **/
				String[] strOutValues = new String[] {strOrgId, strSetlBelongDate, strSetlOrderId, strSetlStatus};
				lstOuterInfo.add(strOutValues);
			}
			
			prst.executeBatch();
			prst.close();
			
			return lstOuterInfo;
		}
		
		/**
		 * 更新中间结算单临时表内结算单ID。
		 * @param listFinalSettleInfo
		 * @param prst
		 * @throws SQLException 
		 */
		private void updateTempSettleOrderNo(List<String[]> lstOuterInfo, Connection conn) throws SQLException {
			String strSql = "update tbl_temp_settle_order set settle_order_id=?, settle_status=? where org_id=? and settle_belong_date=?;";
			PreparedStatement prst = conn.prepareStatement(strSql);
			
			for (String[] strOuterInfo : lstOuterInfo) {
				String strOrgId = strOuterInfo[0];
				String strSetlBelongDate = strOuterInfo[1];
				String strSetlOrderId = strOuterInfo[2];
				String strSetlStatus = strOuterInfo[3];
				
				prst.setString(1, strSetlOrderId);
				prst.setString(2, strSetlStatus);
				prst.setString(3, strOrgId);
				prst.setString(4, strSetlBelongDate);
				
				prst.addBatch();
			}
			
			
			prst.executeBatch();
			prst.close();
		}
		
		/**
		 * 更新中间结算单表状态前，从临时表获取订单号、订单类型、最终结算单ID。
		 * @param listFinalSettleInfo
		 * @return
		 * @throws SQLException 
		 */
		private List<String[]> getMidlSetlInfo(Connection conn) throws SQLException {
			List<String[]> listMidlSetlInfo = new ArrayList<String[]>();
			
			String strSql = "select order_type, out_order_no, org_type, settle_order_id, settle_status from tbl_temp_settle_order group by order_type, out_order_no, org_type, settle_order_id, settle_status;";
			System.out.println("%%%=" + strSql);
			PreparedStatement prst = conn.prepareStatement(strSql);
			ResultSet rs = prst.executeQuery();
			while (rs.next()) {
				String[] strMidlSetlInfo = new String[] {rs.getString("order_type"), rs.getString("out_order_no"), rs.getString("org_type"), rs.getString("settle_order_id"), rs.getString("settle_status")};
				listMidlSetlInfo.add(strMidlSetlInfo);
			}
			
			return listMidlSetlInfo;
		}
		
		/**
		 * 更新[支付单、退款单]结算表中各机构的状态、以及最终结算单号。
		 * @param listMidlSetlInfo
		 * @param conn
		 * @return
		 * @throws SQLException 
		 */
		private void updatePayAndRefDetailInfo(List<String[]> listMidlSetlInfo, Connection conn) throws SQLException {
			
			PreparedStatement prst = null;
			for (String[] strOrderInfos : listMidlSetlInfo) {
				String strOrderType = strOrderInfos[0];
				String strOutOrderNo = strOrderInfos[1];
				String strOrgType = strOrderInfos[2];
				String strSetlOrderId = strOrderInfos[3];
				String strSetlOrderStatus = strOrderInfos[4];
				String strUpSql = "update TABLE set COLUMN_SETTLE_ORDER_ID='" + strSetlOrderId + "', COLUMN_SETTLE_STATUS='" 
									+ strSetlOrderStatus + "' where ORDER_NO='" + strOutOrderNo + "'";
				if (MergPayAndRefundSettleEntity.ORDER_TYPE_PAYMENT.equals(CommonTool.formatNullStrToSpace(strOrderType))) {
					strUpSql = strUpSql.replace("TABLE", "tbl_pay_settle_detail_order").replace("ORDER_NO", "out_trade_no");
				} else if (MergPayAndRefundSettleEntity.ORDER_TYPE_REFUND.equals(CommonTool.formatNullStrToSpace(strOrderType))) {
					strUpSql = strUpSql.replace("TABLE", "tbl_refund_settle_detail_order").replace("ORDER_NO", "out_refund_no");
				}
				
				if (MergPayAndRefundSettleEntity.ORG_TYPE_NOB.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为NOB
					strUpSql = strUpSql.replace("COLUMN_SETTLE_ORDER_ID", "nob_settle_order_id").replace("COLUMN_SETTLE_STATUS", "nob_settle_status");
				} else if (MergPayAndRefundSettleEntity.ORG_TYPE_HARVEST.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Harvest
					strUpSql = strUpSql.replace("COLUMN_SETTLE_ORDER_ID", "har_settle_order_id").replace("COLUMN_SETTLE_STATUS", "har_settle_status");
				} else if (MergPayAndRefundSettleEntity.ORG_TYPE_AGENT.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Agent
					strUpSql = strUpSql.replace("COLUMN_SETTLE_ORDER_ID", "agen_settle_order_id").replace("COLUMN_SETTLE_STATUS", "agen_settle_status");
				} else if (MergPayAndRefundSettleEntity.ORG_TYPE_SUB_MCH.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Submch
					strUpSql = strUpSql.replace("COLUMN_SETTLE_ORDER_ID", "submch_settle_order_id").replace("COLUMN_SETTLE_STATUS", "submch_settle_status");
				}
				
				System.out.println(">>->>strUpSql = " + strUpSql);
				prst = conn.prepareStatement(strUpSql);
				prst.executeUpdate();
				prst.close();
			}
		}
		
		/**
		 * 获取插入结算单中间表所需的订单信息。
		 * @param strSql
		 * @return
		 */
		private List<String[]> getOrgTempOrder(String strSql) {
			List<String[]> listTempInfo = new ArrayList<String[]>();
			
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			PreparedStatement prst = null;
			ResultSet rs = null;
			
			try {
				System.out.println("#++-strSql = " + strSql);
				prst = conn.prepareStatement(strSql);
				rs = prst.executeQuery();
				while (rs.next()) {
					String strOrgId = rs.getString("org_id");
					String strOutOrderNo = rs.getString("out_order_no");
					String strOrgType = rs.getString("org_type");
					String strOrderType = rs.getString("order_type");
					String strSetBelongDate = rs.getString("settle_belong_date");
					String strSetFeeAmount = rs.getString("settle_fee_amount");
					
					System.out.println("strOrgId = " + strOrgId);
					System.out.println("strOutOrderNo = " + strOutOrderNo);
					System.out.println("strOrgType = " + strOrgType);
					System.out.println("strOrderType = " + strOrderType);
					System.out.println("strSetBelongDate = " + strSetBelongDate);
					System.out.println("strSetFeeAmount = " + strSetFeeAmount);
					
					String[] strOrderInfo = new String[] {strOrgId, strOutOrderNo, strOrgType, strOrderType, strSetBelongDate, strSetFeeAmount};
					System.out.println("+++--strOrderInfo = " + strOrderInfo);
					
					listTempInfo.add(strOrderInfo);
					System.out.println("+++--listTempInfo = " + listTempInfo);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
			}
			
			return listTempInfo;
		}
		
		/**
		 * 将各机构获取到的临时数据插入到临时表中。
		 * @param listOrgTempInfo
		 */
		private void insertOrgTempOrder(List<String[]> listOrgTempInfo) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				// 向临时表插入数据
				String strSql = "replace into tbl_temp_settle_order(org_id, out_order_no, org_type, order_type, settle_belong_date, settle_fee_amount) "
								+ " values(?, ?, ?, ?, ?, ?);";
				System.out.println("++-++strSql = " + strSql);
				System.out.println("listOrgTempInfo.size = " + listOrgTempInfo.size());
				prst = conn.prepareStatement(strSql);
				for (int i = 0; i < listOrgTempInfo.size(); i++) {
					String[] strOrderInfo = listOrgTempInfo.get(i);
					prst.setString(1, strOrderInfo[0]);
					prst.setString(2, strOrderInfo[1]);
					prst.setString(3, strOrderInfo[2]);
					prst.setString(4, strOrderInfo[3]);
					prst.setString(5, strOrderInfo[4]);
					prst.setString(6, strOrderInfo[5]);
					prst.addBatch();
				}
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
		 * 获取某个机构当天内最大的的批次号，并将其加1，然后返回。
		 * @param strOrgId
		 * @param strSetlBelongDate
		 * @return
		 * @throws SQLException 
		 */
		private String getPlusedBatchNo(String strOrgId, String strSetlBelongDate) throws SQLException {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
			String strSql = "select (max(SUBSTRING_INDEX(settle_batch_no, '_', -1)) + 1) as plused_batch_no from tbl_settlement_sum_order where org_id='" 
							+ strOrgId + "' and settle_belong_date='" + strSetlBelongDate + "';";
			PreparedStatement prst = conn.prepareStatement(strSql);
			ResultSet rs = prst.executeQuery();
			String strPlusedBatchNo = null;
			if (rs.next()) {
				strPlusedBatchNo = rs.getString("plused_batch_no");
			}
			
			if (strPlusedBatchNo == null || "".equals(strPlusedBatchNo)) {
				strPlusedBatchNo = "0001";
			} else {
				strPlusedBatchNo = CommonTool.getFixLenStr(strPlusedBatchNo, "0", 4);
			}
			
			String strFinlRst = strSetlBelongDate + "_" + strPlusedBatchNo;
			return strFinlRst;
		}
	}
}
