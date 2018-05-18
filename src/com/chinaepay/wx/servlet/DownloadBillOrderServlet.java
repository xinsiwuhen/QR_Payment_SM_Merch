package com.chinaepay.wx.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.common.JsonRespObj;
import com.chinaepay.wx.entity.DownloadBillOrderEntity;
import com.chinaepay.wx.entity.DownloadBillPayOrderEntity;

import net.sf.json.JSONObject;

public abstract class DownloadBillOrderServlet extends InquiryControllerServlet {
	private final String DOWNLOAD_BILL_ORDER_URL = CommonInfo.HONG_KONG_CN_SERVER_URL + "/pay/downloadbill";
	final long lngOneDay = 24 * 60 * 60 * 1000L;
	
	public void init() {
		try {
			super.init();
			
			// 获取下载对账单的指定时间
			String strHour = this.getServletContext().getInitParameter("Hour_DownloadBill");
			String strMinute = this.getServletContext().getInitParameter("Minute_DownloadBill");
			String strSecond = this.getServletContext().getInitParameter("Second_DownloadBill");
			String strDelayTime = this.getServletContext().getInitParameter("DelayTime_DownloadBill");
	
			// 启动下载对账单的任务线程
			DownloadBillOrderThread dbpot = new DownloadBillOrderThread(strHour, strMinute, strSecond, strDelayTime);
			dbpot.start();
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 为前端对账界面预留，保证对账出现差错时，可以由前端界面发起下载对账单的申请。
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		// 获取【startTimeForBillOrderSucc】与【endTimeForBillOrderSucc】之间的所有成功的支付单 
		// 【注意】：时间格式精确到“天”，如：20180302
		String startTimeForBillOrderSucc = request.getParameter("startTimeForBillOrderSucc"); 
		String endTimeForBillOrderSucc = request.getParameter("endTimeForBillOrderSucc");
		
		ClosableTimer closableTimer = new ClosableTimer(true);	// 执行完任务后关闭Timer
		TimerTask task = getNewDownloadBillOrderTask(closableTimer, startTimeForBillOrderSucc, endTimeForBillOrderSucc);
        closableTimer.schedule(task, 0L);
        
        // 将订单查询结果返回到请求终端
        JsonRespObj respObj = new JsonRespObj();
		String strResult = "1";
		String strReturnMsg = "从腾讯侧下载对账单(含：支付单与退款单)任务已经提交后台执行！";
        respObj.setRespCode(strResult);
		respObj.setRespMsg(strReturnMsg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	/**
	 * 生成新的下载对账单任务，回调该类的子类中已经实现的方法。
	 * @return
	 */
	public abstract DownloadBillOrderTask getNewDownloadBillOrderTask(ClosableTimer closableTimer);
	
	/**, endTimeForBillOrderSucc
	 * 生成新的下载对账单任务，回调该类的子类中已经实现的方法。
	 * @param startTimeForBillOrderSucc	所下载的对账单数据的起始时间。
	 * @param endTimeForBillOrderSucc		所下载的对账单数据的结束时间。
	 * @return
	 */
	public abstract DownloadBillOrderTask getNewDownloadBillOrderTask(ClosableTimer closableTimer, String startTimeForBillOrderSucc, String endTimeForBillOrderSucc);
	
	
	public void updateInquiryRstToTbl(Map<String, String> mapArgs) {};
	
	/**
	 * 启动下任对账单任务的主线程。
	 * @author xinwuhen
	 */
	public class DownloadBillOrderThread extends Thread {
		private String strHour = null;
		private String strMinute = null;
		private String strSecond = null;
		private String strDelayTime = null;
		
		public DownloadBillOrderThread(String strHour, String strMinute, String strSecond, String strDelayTime) {
			this.strHour = strHour;
			this.strMinute = strMinute;
			this.strSecond = strSecond;
			this.strDelayTime = strDelayTime;
		}
		
		
		public void run() {
			// 当前时间
			long lngNowMillSec = new Date().getTime();
			
			// 获取指定日历参数的时间
			Date defineDate = getFixDateBasedOnArgs(strHour, strMinute, strSecond);
			long lngDefMillSec = defineDate.getTime();
			
			ClosableTimer closableTimer = null;
			TimerTask task = null;
			// 当前时间在10点之前, 需要等到10点时执行任务，并继续以24小时的轮询周期执行相同任务
			if (lngNowMillSec < lngDefMillSec) {
				closableTimer = new ClosableTimer(false);
		        task = getNewDownloadBillOrderTask(closableTimer);
		        closableTimer.scheduleAtFixedRate(task, defineDate, lngOneDay);
			}
			// 当前时间在10点之后，需要马上执行一次任务，并在次日的10点开始执行一次，以后每隔24小时的轮询周期执行相同任务
			else {
				// 执行一次任务(依据抓取数据的时间)，并在任务结束后关闭
				closableTimer = new ClosableTimer(true);
				task = getNewDownloadBillOrderTask(closableTimer);
				closableTimer.schedule(task, Long.parseLong(strDelayTime) * 60 * 1000);	// 将分钟转换为毫秒
				
				// 在次日10点开始执行一次任务， 以后每隔24小时的轮询周期执行相同任务
				closableTimer = new ClosableTimer(false);
				task = getNewDownloadBillOrderTask(closableTimer);
				Date nextDay = CommonTool.getBefOrAftDate(defineDate, lngOneDay);
				closableTimer.scheduleAtFixedRate(task, nextDay, lngOneDay);
			}
		}
	}
	
	/**
	 * 下载对账单任务类。
	 * @author xinwuhen
	 *
	 */
	public abstract class DownloadBillOrderTask extends TimerTask {
		private ClosableTimer closableTimer = null;
		String startTimeForBillOrderSucc = null;
		String endTimeForBillOrderSucc = null;
		
		public DownloadBillOrderTask(ClosableTimer closableTimer) {
			super();
			this.closableTimer = closableTimer;
		}
		
		public DownloadBillOrderTask(ClosableTimer closableTimer, String startTimeForBillOrderSucc, String endTimeForBillOrderSucc) {
			this(closableTimer);
			this.startTimeForBillOrderSucc = startTimeForBillOrderSucc;
			this.endTimeForBillOrderSucc = endTimeForBillOrderSucc;
		}
		
		/**
		 * 下载对账单，并且将对账单信息更新到数据库。
		 */
		public void downloadAndUpdateBillInfo(String strBillType) {
			String strProcesserId = null;
			String strBillDate = null;
			String strWxRespInfo = null;
			
			// 对账单处理控制表中的【对账单类型】字段
			String strNewBillType = null;
			if (strBillType.equals(DownloadBillPayOrderEntity.SUCCESS)) {
				strNewBillType = DownloadBillPayOrderEntity.BILL_ORDER_PAYMENT;
			} else if (strBillType.equals(DownloadBillPayOrderEntity.REFUND)) {
				strNewBillType = DownloadBillPayOrderEntity.BILL_ORDER_REFUND;
			}
			
			if (startTimeForBillOrderSucc != null && !"".equals(startTimeForBillOrderSucc)) {	// 有开始时间
				if (endTimeForBillOrderSucc == null || "".equals(endTimeForBillOrderSucc)) {	// 结束时间为空，默认结束时间为昨天
					endTimeForBillOrderSucc = CommonTool.getBefOrAftFormatDate(new Date(), -lngOneDay, "yyyyMMdd");
				}
				
				Date startDate = CommonTool.getDateBaseOnChars("yyyyMMdd", startTimeForBillOrderSucc);
				long lngStartTime = startDate.getTime();
				long lngEndTime = CommonTool.getDateBaseOnChars("yyyyMMdd", endTimeForBillOrderSucc).getTime();
				long lngDayIndx = lngStartTime;
				while (lngDayIndx <= lngEndTime) {	// 开始时间不晚于结束时间
					// 向腾讯发送对账单查询请求，并依据返回的结果更新对账单数据表
					strBillDate = CommonTool.getFormatDateStr(startDate, "yyyyMMdd");
					
					// 向对账单下载控制类添加一条新记录，用于记录对账单的下载及处理情况
					strProcesserId = this.insertBillProcInfoToTbl(strBillDate, strNewBillType);
					
					// 向腾讯后台发送请求，并返回应答结果
					strWxRespInfo = this.getBillRespInfoFromWx(strBillType, strBillDate);	// 支付成功的订单（支付单）
					this.parseRespInfoAndUpdateTbl(strProcesserId, strWxRespInfo);
					
					// 取得一天后的时间
					startDate = CommonTool.getBefOrAftDate(startDate, lngOneDay);
					lngDayIndx = startDate.getTime();
				}
			} else {	// 开始时间为空，默认取昨天的数据
				strBillDate = CommonTool.getBefOrAftFormatDate(new Date(), -lngOneDay, "yyyyMMdd");
				
				// 向对账单下载控制类添加一条新记录，用于记录对账单的下载及处理情况
				strProcesserId = this.insertBillProcInfoToTbl(strBillDate, strNewBillType);
		        
				strWxRespInfo = this.getBillRespInfoFromWx(strBillType, strBillDate);	// 支付成功的订单（支付单）
				
				this.parseRespInfoAndUpdateTbl(strProcesserId, strWxRespInfo);
			}
			
			// 判断是否需要关闭任务时钟
			if (closableTimer.isNeedClose()) {
				closableTimer.cancel();
			}
		}
		
		/**
		 * 向对账单处理表插入初始化数据。
		 * @param strBillDate
		 * @param strNewBillType
		 */
		private String insertBillProcInfoToTbl(String strBillDate, String strNewBillType) {
			String strProcesserId = "";
			
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				strProcesserId = CommonTool.getRandomString(16);
				String strSql = "replace into tbl_bill_order_proc_result(processer_id, mch_id, bill_order_type, belong_date, proc_start_time, proc_finish_time, proc_status) values('"
								+ strProcesserId
								+ "', '"
								+ CommonInfo.NOB_MCH_ID
								+ "', '"
								+ strNewBillType
								+ "', '"
								+ strBillDate
								+ "', '"
								+ CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss")
								+ "', '', '"
								+ DownloadBillOrderEntity.BILL_PROCESSING
								+ "')";
				prst = conn.prepareStatement(strSql);
				prst.execute();
				conn.commit();
			} catch(SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
			
			return strProcesserId;
		}
		
		/**
		 * 更新对账单处理控制类内的记录状态。
		 * @param strProcesserId
		 * @param strProcStatus
		 */
		private void updateBillProcInfoToTbl(String strProcesserId, String strProcStatus) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				String strSql = "update tbl_bill_order_proc_result set proc_finish_time='"
								+ CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss")
								+ "', proc_status='"
								+ strProcStatus
								+ "' where processer_id='"
								+ strProcesserId
								+ "';";
				prst = conn.prepareStatement(strSql);
				prst.executeUpdate();
				conn.commit();
			} catch(SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
		}
		
		/**
		 * 解析微信返回的应答内容，并依此确定更新数据库表的内容。
		 * @param strWxRespInfo
		 */
		private void parseRespInfoAndUpdateTbl(String strProcesserId, String strWxRespInfo) {
			if (strWxRespInfo.toLowerCase().startsWith("<xml>")) {	// 下载对账单出错
				System.out.println("strWxRespInfo = " + strWxRespInfo);
				Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strWxRespInfo);
				String strReturnCode = mapWxRespInfo.get(DownloadBillOrderEntity.RETURN_CODE);
				if (strReturnCode != null && strReturnCode.equals(DownloadBillOrderEntity.FAIL)) {
					String strReturnMsg = mapWxRespInfo.get(DownloadBillOrderEntity.RETURN_MSG);
					System.out.println("下载对账单时出错，出错信息为：" + strReturnMsg);
				}
				
				// 更新对账单处理类状态为【处理失败】
				this.updateBillProcInfoToTbl(strProcesserId, DownloadBillOrderEntity.BILL_PROC_FAIL);
			} else {	// 解析对账单数据内容, 并更新到数据库中
				List<String> lstBillOrderData = this.getBillOrderData(strWxRespInfo);
				
				// 生成更新支付对账单或退款退账单的SQL语句
				String strBatchUpSql = this.getUpdateBillOrderBatchSql();
				boolean blnUpBillRst = updateBillOrderToTbl(strBatchUpSql, lstBillOrderData);
				
				String strBillProcRst = null;
				if (blnUpBillRst) {
					strBillProcRst = DownloadBillOrderEntity.BILL_PROC_SUCCESS;
				} else {
					strBillProcRst = DownloadBillOrderEntity.BILL_PROC_FAIL;
				}
				
				// 更新对账单下载及入库时的执行结果
				this.updateBillProcInfoToTbl(strProcesserId, strBillProcRst);
			}
		}
		
		/**
		 * 解析对账单数据体中的内容，并以字符串数据进行存储。
		 * 每个字符串格式为：data1, data2, data3, ... ...
		 * @param strWxRespInfo
		 * @return
		 */
		private List<String> getBillOrderData(String strWxRespInfo) {
			List<String> lstBillOrderData = new ArrayList<String>();
			BufferedReader br = new BufferedReader(new StringReader(strWxRespInfo));
			String strLine = "";
			while (true) {
				try {
					strLine = br.readLine();
					if (strLine == null) {
						break;
					} else {
						if (strLine.startsWith("`")) {	// 对账单内的真实数据(数据体)
							String strOrderData = strLine.replaceAll("`", "");
							lstBillOrderData.add(strOrderData);
						} else if (strLine.toLowerCase().startsWith("Total transaction count".toLowerCase())) {	// 不提取对账单内最后两行的汇总数据
							break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
			
			return lstBillOrderData;
		}
		
		/**
		 * 更新最终的结果（从微信端拿到的对账单数据）到数据库。
		 * @param strBatchUpSql
		 * @param lstBillOrderData
		 * @return
		 */
		private boolean updateBillOrderToTbl(String strBatchUpSql, List<String> lstBillOrderData) {
			boolean blnUpBillRst = false;
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			
			try {
				System.out.println("&&&strBatchUpSql = " + strBatchUpSql);
				prst = conn.prepareStatement(strBatchUpSql);
				
				int iBillDataSize = lstBillOrderData.size();
				for (int i = 0; i < iBillDataSize; i++) {
					String strLine = lstBillOrderData.get(i);
					
					String[] strValue = strLine.split(",");
					for (int j = 0; j < strValue.length; j++) {
						prst.setString(j + 1, strValue[j]);
					}
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
				
				// 标记整个对账单入库完成
				blnUpBillRst = true;
				
			} catch(SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
				
				blnUpBillRst = false;
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
			
			return blnUpBillRst;
		}
		
		/**
		 * 生成不同的对账单表所需要的SQL语句（批量更新）。
		 * @return
		 */
		public abstract String getUpdateBillOrderBatchSql();
		
		/**
		 * 依据订单类型（支付单 or 退款单），从腾讯下载对账单信息。
		 * @param strBillType
		 * @return
		 */
		private String getBillRespInfoFromWx(String strBillType, String strBillDate) {
			if (strBillType == null || "".equals(strBillType) || strBillDate == null && "".equals(strBillDate)) {
				return null;
			}
			
			// 向腾讯后台发送请求前，整合请求参数
			Map<String, String> mapInquiryOrderArgs = new HashMap<String, String>();
			mapInquiryOrderArgs.put(DownloadBillOrderEntity.NONCE_STR, CommonTool.getRandomString(32));
			mapInquiryOrderArgs.put(DownloadBillOrderEntity.BILL_DATE, strBillDate);
			mapInquiryOrderArgs.put(DownloadBillOrderEntity.BILL_TYPE, strBillType);
			mapInquiryOrderArgs = CommonTool.getAppendMap(mapInquiryOrderArgs, CommonTool.getHarvestTransInfo());
			mapInquiryOrderArgs.put(DownloadBillOrderEntity.SIGN, CommonTool.getEntitySign(mapInquiryOrderArgs));
			
			// 向腾讯后台发查询请求
			String strReqInfo = formatReqInfoToXML(mapInquiryOrderArgs);
			System.out.println("strReqInfo = " + strReqInfo);
			String strWxRespInfo = sendReqAndGetResp(DOWNLOAD_BILL_ORDER_URL, strReqInfo, CommonTool.getDefaultHttpClient());
			
			System.out.println(">>**strWxRespInfo = " + strWxRespInfo);
			
			return strWxRespInfo;
		}
	}
}
