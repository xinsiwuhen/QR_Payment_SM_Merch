/**
 * @author xinwuhen
 */
package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.ServletException;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.PayOrderEntiry;

/**
 * 查询交易单信息接口。
 * @author xinwuhen
 */
public class InquiryPayOrderServlet extends InquiryControllerServlet {
	private final String INQUIRY_PAY_ORDER_URL = CommonInfo.HONG_KONG_CN_SERVER_URL + "/pay/orderquery";
	
	// 线程池对象
	private static ThreadPoolExecutor threadPoolExecutor = null;
	
	// 核心线程大小
	private static final int iCorePoolSize = 500;
	// 最大线程数
	private static final int intMaxPoolSize = 3000;
	// 当前线程池内线程数量，超过核心线程大小时，空闲线程允许的最大时间值（单位：秒）
	private static final int intKeepAliveTime = 1 * 60;	// 1分钟
	// 任务队列的最大值
	private static final int intTaskQueueSize = Integer.MAX_VALUE;
	
	public void init() {
		try {
			super.init();
			
			// 创建线程池，每个线程用于执行支付订单的查询以及应答报文更新到数据的操作
			if (threadPoolExecutor == null) {
				threadPoolExecutor = super.getThreadPoolExecutor(iCorePoolSize, intMaxPoolSize, intKeepAliveTime, intTaskQueueSize);
			}
			
			// 启动校验支付订单状态的主线程
			ValidatePayOrderThread vlidPayOrderThread = new ValidatePayOrderThread(threadPoolExecutor);
			vlidPayOrderThread.start();
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
//	public void doGet(HttpServletRequest request, HttpServletResponse response) {
//		// 商户订单号
//		String strOutTradeNo = request.getParameter("out_trade_no");
//		
//		// 向腾讯后台发送支付单查询请求，并依据获得的应答报文更新相关信息
//		Map<String, String> mapWxRespInfo = this.getAndUpdatePayOrderRespInfo(strOutTradeNo);
//		
//		// 将订单查询结果返回到请求终端
//		String strDispatcherURL = null;
//		String strReturnCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(PayOrderEntiry.RETURN_CODE));
//		String strResultCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(PayOrderEntiry.RESULT_CODE));
//		String strTradeStatus = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(PayOrderEntiry.TRADE_STATE));
//		if (strReturnCode.equals(PayOrderEntiry.SUCCESS) 
//				&& strResultCode.equals(PayOrderEntiry.SUCCESS)
//				&& strTradeStatus.equals(PayOrderEntiry.SUCCESS)) {
//			strDispatcherURL = "../sucess.jsp?outTradeNo=" + CommonTool.formatNullStrToSpace(strOutTradeNo) 
//								+ "&paymentResult=" + PayOrderEntiry.SUCCESS + "&msg=" + mapWxRespInfo.get(PayOrderEntiry.TRADE_STATE_DESC);
//		} else if (strReturnCode.equals(PayOrderEntiry.FAIL)) {
//			strDispatcherURL = "../error.jsp?msg=" + mapWxRespInfo.get(PayOrderEntiry.RETURN_MSG);
//		} else if (strResultCode.equalsIgnoreCase(PayOrderEntiry.FAIL)) {
//			strDispatcherURL = "../error.jsp?msg=" + mapWxRespInfo.get(PayOrderEntiry.ERR_CODE_DES);
//		} else {
//			strDispatcherURL = "../error.jsp?msg=" + mapWxRespInfo.get(PayOrderEntiry.TRADE_STATE_DESC);
//		}
//		
//		System.out.println("strDispatcherURL = " + strDispatcherURL);
//		try {
//			request.getRequestDispatcher(strDispatcherURL).forward(request, response);
//		} catch (ServletException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
	
//	public void doPost(HttpServletRequest request, HttpServletResponse response) {
//		this.doGet(request, response);
//	}

	@Override
	public void updateInquiryRstToTbl(Map<String, String> mapArgs) {
		if (mapArgs == null || mapArgs.size() == 0) {
			return;
		}
		
		Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
		PreparedStatement prst = null;
		
		try {
			String strSql = "update tbl_trans_order set transaction_id='" 
							+ mapArgs.get(PayOrderEntiry.TRANSACTION_ID) 
							+ "', time_end='"
							+ mapArgs.get(PayOrderEntiry.TIME_END)
							+"', trade_state='"
							+ mapArgs.get(PayOrderEntiry.TRADE_STATE)
							+ "', trade_state_desc='"
							+ mapArgs.get(PayOrderEntiry.TRADE_STATE_DESC) 
							+ "', bank_type='"
							+ mapArgs.get(PayOrderEntiry.BANK_TYPE) 
							+ "', cash_fee='"
							+ mapArgs.get(PayOrderEntiry.CASH_FEE) 
							+ "', cash_fee_type='"
							+ mapArgs.get(PayOrderEntiry.CASH_FEE_TYPE) 
							+ "', rate='"
							+ mapArgs.get(PayOrderEntiry.RATE) 
							+ "' where out_trade_no='"
							+ mapArgs.get(PayOrderEntiry.OUT_TRADE_NO)
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
	 * 向腾讯后台发送支付单查询请求，并依据获得的应答报文更新相关信息。
	 * @param strOutTradeNo
	 */
	private Map<String, String> getPayOrderRespInfo(String strOutTradeNo) {
		// 通过商户订单号，获取子商户ID
		Map<String, String> mapArgs = new HashMap<String, String>();
		mapArgs.put("out_trade_no", strOutTradeNo);
		String strSubMerchId = super.getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
//		String strTransactionId = super.getTblFieldValue("transaction_id", "tbl_trans_order", mapArgs);
		
		// 向腾讯后台发送请求前，整合请求参数
		Map<String, String> mapInquiryOrderArgs = new HashMap<String, String>();
		mapInquiryOrderArgs.put(PayOrderEntiry.SUB_MCH_ID, CommonTool.formatNullStrToSpace(strSubMerchId));
//		mapInquiryOrderArgs.put(PayOrderEntiry.TRANSACTION_ID, CommonTool.formatNullStrToSpace(strTransactionId));
		mapInquiryOrderArgs.put(PayOrderEntiry.OUT_TRADE_NO, CommonTool.formatNullStrToSpace(strOutTradeNo));
		mapInquiryOrderArgs.put(PayOrderEntiry.NONCE_STR, CommonTool.getRandomString(32));
		mapInquiryOrderArgs = CommonTool.getAppendMap(mapInquiryOrderArgs, CommonTool.getHarvestTransInfo());
		mapInquiryOrderArgs.put(PayOrderEntiry.SIGN, CommonTool.getEntitySign(mapInquiryOrderArgs));
		
		// 向腾讯后台发查询请求
		String strReqInfo = super.formatReqInfoToXML(mapInquiryOrderArgs);
		System.out.println("strReqInfo >>>>>= " + strReqInfo);
		String strWxRespInfo = super.sendReqAndGetResp(INQUIRY_PAY_ORDER_URL, strReqInfo, CommonTool.getDefaultHttpClient());
		System.out.println("strWxRespInfo> = " + strWxRespInfo);
		Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strWxRespInfo);
		System.out.println("<<>>>>>mapWxRespInfo = " + mapWxRespInfo);
		
		return mapWxRespInfo;
	}
	
	/**
	 * 从支付单表查询状态为【未支付、支付中】的订单ID。
	 * @param strField
	 * @param strTblName
	 * @return
	 */
	private List<String> getNoOrPayingOutTradeNo(String strField, String strTblName) {
		List<String[]> listArgs = new ArrayList<String[]>();
		
		// 从支付单表查询状态为【未支付】的支付单ID
		listArgs.add(new String[] {"trade_state", "=", PayOrderEntiry.NOTPAY});
//		Date newDate = new Date(new Date().getTime() - 3 * 60 * 60 * 1000L);	// 只取3小时之内的订单
//		listArgs.add(new String[] {"time_start", ">", CommonTool.getFormatDateStr(newDate, "yyyyMMddHHmmss")});	
		List<String> lstNoPayOutTradeNo = super.getTblFieldValueList(strField, strTblName, listArgs);
		
		// 从支付单表查询状态为【支付中】的支付单ID
		listArgs.clear();
		listArgs.add(new String[] {"trade_state", "=", PayOrderEntiry.USERPAYING});
//		listArgs.add(new String[] {"time_start", ">", CommonTool.getFormatDateStr(newDate, "yyyyMMddHHmmss")});
		List<String> lstPayingOutTradeNo = super.getTblFieldValueList(strField, strTblName, listArgs);
		
		List<String> listNew = CommonTool.getCloneList(lstNoPayOutTradeNo);
		return CommonTool.getAppendList(listNew, lstPayingOutTradeNo);
	}
	
	/**
	 * 校验支付订单状态的主线程。
	 * @author xinwuhen
	 */
	public class ValidatePayOrderThread extends Thread {
		private ThreadPoolExecutor threadPoolExecutor = null;
		
		public ValidatePayOrderThread(ThreadPoolExecutor threadPoolExecutor) {
			this.threadPoolExecutor = threadPoolExecutor;
		}
		
		public void run() {
			// 定期执行符合条件的支付单状态确认操作(向腾讯后台查询支付单状态)
			while (true) {
				// 从支付单表查询状态为【未支付、支付中】的订单ID
				List<String> listOutTradeNo = getNoOrPayingOutTradeNo("out_trade_no", "tbl_trans_order");
				System.out.println("listOutTradeNo = " + listOutTradeNo);
				
				// 启动支付单查询线程（线程内完成支付单状态查询，并将状态更新到支付单表）
				if (listOutTradeNo != null) {
					for (String strOutTradeNo : listOutTradeNo) {
						threadPoolExecutor.execute(new ValidPaymentOrderRunnable(strOutTradeNo));
					}
				}
				
				// 1分钟执行一次
				try {
					Thread.currentThread().sleep(INQUIRY_ORDER_WAIT_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 校验支付单状态的Runnable接口。
	 * @author xinwuhen
	 */
	public class ValidPaymentOrderRunnable implements Runnable {
		private String strOutTradeNo = null;
		
		public ValidPaymentOrderRunnable(String strOutTradeNo) {
			this.strOutTradeNo = strOutTradeNo;
		}
		
		@Override
		public void run() {
			// 向腾讯后台发送支付单查询请求，并获得应答结果
			Map<String, String> mapWxRespInfo = getPayOrderRespInfo(strOutTradeNo);
			
			// 更新数据库中支付单相关的信息
			updateInquiryRstToTbl(mapWxRespInfo);
			
			// 腾讯侧返回的支付单状态仍然为“未支付”或“支付中”时，并且已经超过订单有效时间(time_expire)，则调用关单接口，对支付单进行关闭
			closeTimeOutPayOrder(mapWxRespInfo);
		}
		
		/**
		 * 腾讯侧返回的支付单状态仍然为“未支付”或“支付中”时，并且已经超过订单有效时间(time_expire)，则调用关单接口，对支付单进行关闭。
		 * @param strTradeStatus	腾讯侧返回的最新支付单状态。
		 */
		private void closeTimeOutPayOrder(Map<String, String> mapWxRespInfo) {
			String strReturnCode = mapWxRespInfo.get(PayOrderEntiry.RETURN_CODE);
			String strResultCode = mapWxRespInfo.get(PayOrderEntiry.RESULT_CODE);
			
			if (strReturnCode == null || "".equals(strReturnCode) || !strReturnCode.equals(PayOrderEntiry.SUCCESS)) {	
				return;
			} else {	// ReturnCode状态为SUCESS
				Map<String, String> mapArgs = new HashMap<String, String>();
				mapArgs.put("out_trade_no", strOutTradeNo);
				String strSubMerchId = getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
				String strOrderTimeExpire = getTblFieldValue("time_expire", "tbl_trans_order", mapArgs);
				
				long lngOrderTimeExpire = CommonTool.getDateBaseOnChars("yyyyMMddHHmmss", strOrderTimeExpire).getTime();
				long lngCurrTime = new Date().getTime();
				System.out.println("lngCurrTime = " + lngCurrTime);
				System.out.println("lngOrderTimeExpire = " + lngOrderTimeExpire);
				
				if (strResultCode == null || "".equals(strResultCode) || !strResultCode.equals(PayOrderEntiry.SUCCESS)) {
					String strErrCode = mapWxRespInfo.get(PayOrderEntiry.ERR_CODE);
					if (strErrCode != null && strErrCode.equals(PayOrderEntiry.ORDERNOTEXIST)) {	// 交易订单号不存在，该API只能查提交支付交易返回成功的订单，请商户检查需要查询的订单号是否正确
						if (lngCurrTime > lngOrderTimeExpire) {	// 当前时间超出支付单有效时间，调用关闭订单接口
							// 调用关单接口
							new CloseOrderServlet().sendCloseReqAndUpdateTbl(strSubMerchId, strOutTradeNo);
						}
					}
				} else {	// ResultCode状态为SUCESS
					String strTradeStatus =  mapWxRespInfo.get(PayOrderEntiry.TRADE_STATE);
					if (strTradeStatus != null && (strTradeStatus.equals(PayOrderEntiry.NOTPAY) || strTradeStatus.equals(PayOrderEntiry.USERPAYING))) {
						if (lngCurrTime > lngOrderTimeExpire) {	// 当前时间超出支付单有效时间，调用关闭订单接口
							// 调用关单接口
							new CloseOrderServlet().sendCloseReqAndUpdateTbl(strSubMerchId, strOutTradeNo);
						}
					}
				}
			}
		}
	}
}
