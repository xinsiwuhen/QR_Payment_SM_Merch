package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.CloseOrderEntity;

/**
 * 订单关闭接口。以下情况需要调用关单接口：
 * 商户订单支付失败需要生成新单号重新发起支付，要对原订单号调用关单，避免重复支付；系统下单后，用户支付超时，系统退出不再受理，避免用户继续，请调用关单接口。
 * @author xinwuhen
 *
 */
public class CloseOrderServlet extends TransControllerServlet {
	private final String CLOSE_ORDER_URL = CommonInfo.HONG_KONG_CN_SERVER_URL + "/pay/closeorder";
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		String strSubMerchId = request.getParameter("sub_mch_id");
		String strOutTradeNo = request.getParameter("out_trade_no");
		System.out.println("strSubMerchId = " + strSubMerchId);
		System.out.println("strOutTradeNo = " + strOutTradeNo);
		
		// 向腾讯后台发送关闭订单请求，并根据应答结果更新数据库
		this.sendCloseReqAndUpdateTbl(strSubMerchId, strOutTradeNo);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	@Override
	public boolean insertOrderInfoToTbl(Map<String, String> mapArgs) {
		return true;
	}
	
	/**
	 * 向腾讯后台发送关闭订单请求，并根据应答结果更新数据库。
	 * @param strSubMerchId
	 * @param strOutTradeNo
	 */
	public void sendCloseReqAndUpdateTbl(String strSubMerchId, String strOutTradeNo) {
		/** 生成请求报文内容 **/
		Map<String, String> mapCloseOrderReqInfo = new HashMap<String, String>();
		mapCloseOrderReqInfo.put(CloseOrderEntity.SUB_MCH_ID, CommonTool.formatNullStrToSpace(strSubMerchId));
		mapCloseOrderReqInfo.put(CloseOrderEntity.OUT_TRADE_NO, CommonTool.formatNullStrToSpace(strOutTradeNo));
		mapCloseOrderReqInfo.put(CloseOrderEntity.NONCE_STR, CommonTool.getRandomString(32));
		mapCloseOrderReqInfo = CommonTool.getAppendMap(mapCloseOrderReqInfo, CommonTool.getHarvestTransInfo());
		mapCloseOrderReqInfo.put(CloseOrderEntity.SIGN, CommonTool.getEntitySign(mapCloseOrderReqInfo));
		
		/** 格式化请求报文内容为XML格式 **/
		String strReqInfo = super.formatReqInfoToXML(mapCloseOrderReqInfo);
		System.out.println("cls_strReqInfo = " + strReqInfo);
		
		/** 向腾讯后台发送报文,并接收应答报文 **/
		String strWxRespInfo = super.sendReqAndGetResp(CLOSE_ORDER_URL, strReqInfo, CommonTool.getDefaultHttpClient());
		System.out.println("cls_strWxRespInfo = " + strWxRespInfo);
		Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strWxRespInfo);
		System.out.println("cls_mapWxRespInfo = " + mapWxRespInfo);
		
		/** 处理应答报文，并将关单结果更新到后台数据库 **/
		mapWxRespInfo.put(CloseOrderEntity.OUT_TRADE_NO, CommonTool.formatNullStrToSpace(strOutTradeNo));
		updateOrderRstToTbl(mapWxRespInfo);
	}

	@Override
	public boolean updateOrderRstToTbl(Map<String, String> mapArgs) {
		boolean blnUpRst = false;
		String strReturnCode = mapArgs.get(CloseOrderEntity.RETURN_CODE);
		String strResultCode = mapArgs.get(CloseOrderEntity.RESULT_CODE);
		String strOutTradeNo = mapArgs.get(CloseOrderEntity.OUT_TRADE_NO);
		
		if (CommonTool.formatNullStrToSpace(strReturnCode).equals(CloseOrderEntity.SUCCESS) 
				&& CommonTool.formatNullStrToSpace(strResultCode).equals(CloseOrderEntity.SUCCESS)) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			String strSql = "update tbl_trans_order set trade_state='" + CloseOrderEntity.CLOSED + "', trade_state_desc='已关闭' "
							+ " where out_trade_no='" + CommonTool.formatNullStrToSpace(strOutTradeNo) + "';";
			System.out.println("+-=+strSql = " + strSql);
			try {
				prst = conn.prepareStatement(strSql);
				prst.execute();
				conn.commit();
				blnUpRst = true;
				
			} catch(SQLException se) {
				se.printStackTrace();
				MysqlConnectionPool.getInstance().rollback(conn);
				blnUpRst = false;
			} finally {
				MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
			}
		}
		
		return blnUpRst;
	}
}
