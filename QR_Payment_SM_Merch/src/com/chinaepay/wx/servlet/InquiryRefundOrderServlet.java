package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.ServletException;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.RefundOrderEntity;

/**
 * 查询退款单信息接口。
 * @author xinwuhen
 */
public class InquiryRefundOrderServlet extends InquiryControllerServlet {
	private final String INQUIRY_REFUND_ORDER_URL = CommonInfo.HONG_KONG_CN_SERVER_URL + "/pay/refundquery";
	
	// 线程池对象
	private static ThreadPoolExecutor threadPoolExecutor = null;
	
	// 核心线程大小
	private static final int iCorePoolSize = 200;
	// 最大线程数
	private static final int intMaxPoolSize = 3000;
	// 当前线程池内线程数量，超过核心线程大小时，空闲线程允许的最大时间值（单位：秒）
	private static final int intKeepAliveTime = 1 * 60;	// 1分钟
	// 任务队列的最大值
	private static final int intTaskQueueSize = Integer.MAX_VALUE;
	
	public void init() {
		try {
			super.init();
			
			// 创建线程池，每个线程用于执行退款单的查询以及应答报文更新到数据的操作
			if (threadPoolExecutor == null) {
				threadPoolExecutor = super.getThreadPoolExecutor(iCorePoolSize, intMaxPoolSize, intKeepAliveTime, intTaskQueueSize);
			}
			
			// 启动校验退款单状态的主线程
			ValidateRefundOrderThread vlidRefundOrderThread = new ValidateRefundOrderThread(threadPoolExecutor);
			vlidRefundOrderThread.start();
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
//	public void doGet(HttpServletRequest request, HttpServletResponse response) {
//		// 获取退款单号
//		String strOutRefundNo = request.getParameter("out_refund_no");
//		
//
//		// 向腾讯后台发送退款单查询请求，并依据获得的应答报文更新相关信息
//		Map<String, String> mapWxRespInfo = this.getAndUpdateRefundOrderRespInfo(strOutRefundNo);
//		
//		/**
//		// 将订单查询结果返回到请求终端
//		String strDispatcherURL = null;
//		String strReturnCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(RefundOrderEntity.RETURN_CODE));
//		String strResultCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(RefundOrderEntity.RESULT_CODE));
//		String strTradeStatus = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(RefundOrderEntity.TRADE_STATE));
//		if (strReturnCode.equals(RefundOrderEntity.SUCCESS) 
//				&& strResultCode.equals(RefundOrderEntity.SUCCESS)
//				&& strTradeStatus.equals(RefundOrderEntity.SUCCESS)) {
//			strDispatcherURL = "../sucess.jsp?outRefundNo=" + CommonTool.formatNullStrToSpace(strOutRefundNo) 
//								+ "&refundResult=" + RefundOrderEntity.SUCCESS + "&msg=退款成功！";
//		} else if (strReturnCode.equals(RefundOrderEntity.FAIL)) {
//			strDispatcherURL = "../error.jsp?msg=" + mapWxRespInfo.get(RefundOrderEntity.RETURN_MSG);
//		} else if (strResultCode.equalsIgnoreCase(RefundOrderEntity.FAIL)) {
//			strDispatcherURL = "../error.jsp?msg=" + mapWxRespInfo.get(RefundOrderEntity.ERR_CODE_DES);
//		} else {
//			strDispatcherURL = "../error.jsp?msg=数据错误！";
//		}
//		
//		try {
//			System.out.println("strDispatcherURL = " + strDispatcherURL);
//			request.getRequestDispatcher(strDispatcherURL).forward(request, response);
//		} catch (ServletException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		**/
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
			String strSql = "update tbl_refund_order set transaction_id='"
							+ mapArgs.get(RefundOrderEntity.TRANSACTION_ID) 
							+ "', refund_account='"
							+ mapArgs.get(RefundOrderEntity.REFUND_ACCOUNT + "_0") 
							+ "', refund_count='" 
							+ mapArgs.get(RefundOrderEntity.REFUND_COUNT) 
							+ "', refund_channel='"
							+ mapArgs.get(RefundOrderEntity.REFUND_CHANNEL + "_0") 
							+ "', refund_status='"
							+ mapArgs.get(RefundOrderEntity.REFUND_STATUS + "_0") 	// 由于查询时的请请参数带out_refund_no，所以此处仅返回唯一的退款单信息
							+ "', refund_recv_accout='"
							+ CommonTool.formatNullStrToSpace(mapArgs.get(RefundOrderEntity.REFUND_RECV_ACCOUT + "_0"))
							+ "', refund_success_time='"
							+ CommonTool.formatNullStrToSpace(mapArgs.get(RefundOrderEntity.REFUND_SUCCESS_TIME + "_0"))
							+ "', rate='"
							+ mapArgs.get(RefundOrderEntity.RATE)
							+ "' where out_refund_no='"
							+ mapArgs.get(RefundOrderEntity.OUT_REFUND_NO + "_0")
							+ "';";
			System.out.println(">><<>>strSql = " + strSql);
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
	 * 向腾讯后台发送退款单查询请求，并依据获得的应答报文更新相关信息。
	 * @param strOutRefundNo
	 * @return
	 */
	private Map<String, String> getRefundOrderRespInfo(String strOutRefundNo) {
		Map<String, String> mapArgs = new HashMap<String, String>();
		mapArgs.put("out_refund_no", strOutRefundNo);
		// 获取商户订单号
		String strOutTradeNo = super.getTblFieldValue("out_trade_no", "tbl_trans_order_refund_order", mapArgs);
		
		mapArgs.clear();
		mapArgs.put("out_trade_no", strOutTradeNo);
		// 获取子商户号
		String strSubMerchId = super.getTblFieldValue("sub_mch_id", "tbl_submch_trans_order", mapArgs);
		
		// 向腾讯后台发送请求前，整合请求参数
		Map<String, String> mapInquiryOrderArgs = new HashMap<String, String>();
		mapInquiryOrderArgs.put(RefundOrderEntity.SUB_MCH_ID, CommonTool.formatNullStrToSpace(strSubMerchId));
		mapInquiryOrderArgs.put(RefundOrderEntity.NONCE_STR, CommonTool.getRandomString(32));
		mapInquiryOrderArgs.put(RefundOrderEntity.OUT_TRADE_NO, CommonTool.formatNullStrToSpace(strOutTradeNo));
		mapInquiryOrderArgs.put(RefundOrderEntity.OUT_REFUND_NO, CommonTool.formatNullStrToSpace(strOutRefundNo));
		mapInquiryOrderArgs = CommonTool.getAppendMap(mapInquiryOrderArgs, CommonTool.getHarvestTransInfo());
		mapInquiryOrderArgs.put(RefundOrderEntity.SIGN, CommonTool.getEntitySign(mapInquiryOrderArgs));
		
		// 向腾讯后台发查询请求
		String strReqInfo = super.formatReqInfoToXML(mapInquiryOrderArgs);
		System.out.println("!!!!strReqInfo = " + strReqInfo);
		String strWxRespInfo = super.sendReqAndGetResp(INQUIRY_REFUND_ORDER_URL, strReqInfo, CommonTool.getDefaultHttpClient());
		System.out.println("****strWxRespInfo = " + strWxRespInfo);
		Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strWxRespInfo);
		System.out.println("@@@@strWxRespInfo = " + strWxRespInfo);
		
		return mapWxRespInfo;
	}
	
	/**
	 * 从退款单表查询状态为【退款处理中】的订单ID。
	 * @param strField
	 * @param strTblName
	 * @return
	 */
	private List<String> getRefundProcessingOutRefundNo(String strField, String strTblName) {
		List<String[]> listArgs = new ArrayList<String[]>();
		
		listArgs.add(new String[] {"refund_status", "=", RefundOrderEntity.PROCESSING});
		
		Date newDate =CommonTool.getBefOrAftDate(new Date(), Calendar.YEAR, -1);	// 腾讯侧对于交易时间超过一年的订单无法提交退款；
		listArgs.add(new String[] {"refund_trans_time", ">", CommonTool.getFormatDateStr(newDate, "yyyyMMddHHmmss")});	
		List<String> lstRefundNo = super.getTblFieldValueList(strField, strTblName, listArgs);
		
		return lstRefundNo;
	}
	
	/**
	 * 校验退款单状态的主线程。
	 * @author xinwuhen
	 */
	public class ValidateRefundOrderThread extends Thread {
		private ThreadPoolExecutor threadPoolExecutor = null;
		
		public ValidateRefundOrderThread(ThreadPoolExecutor threadPoolExecutor) {
			this.threadPoolExecutor = threadPoolExecutor;
		}
		
		public void run() {
			// 定期执行符合条件的退款单状态确认操作(向腾讯后台查询退款单状态)
			while (true) {
				// 从退款单表查询状态为【退款处理中】的退款单ID
				List<String> listOutRefundNo = getRefundProcessingOutRefundNo("out_refund_no", "tbl_refund_order");
				System.out.println("listOutRefundNo = " + listOutRefundNo);
				
				// 启动退款单查询线程（线程内完成退款单状态查询，并将状态更新到退款单表）
				if (listOutRefundNo != null) {
					for (String strRefundNo : listOutRefundNo) {
						threadPoolExecutor.execute(new ValidRefundOrderRunnable(strRefundNo));
					}
				}
				
				// 30秒检查一次，看是否退款完成
				try {
					Thread.currentThread().sleep(INQUIRY_ORDER_WAIT_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 校验退款单状态的Runnable接口。
	 * @author xinwuhen
	 */
	public class ValidRefundOrderRunnable implements Runnable {
		private String strRefundNo = null;
		
		public ValidRefundOrderRunnable(String strRefundNo) {
			this.strRefundNo = strRefundNo;
		}
		
		@Override
		public void run() {
			// 向腾讯后台发送退款单查询请求，并依据获得的应答报文更新相关信息
			Map<String, String> mapWxRespInfo = getRefundOrderRespInfo(strRefundNo);
			
			// 更新数据库中退款单相关的信息
			updateInquiryRstToTbl(mapWxRespInfo);
		}
	}
}
