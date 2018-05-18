package com.chinaepay.wx.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.PayOrderEntiry;

/**
 * 创建商户订单，并调用腾讯端的统一下单API。
 * @author xinwuhen
 */
public class PayOrderServlet extends TransControllerServlet {
	private final String UNION_PAY_ORDER_URL = CommonInfo.HONG_KONG_CN_SERVER_URL + "/pay/unifiedorder";
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		String strSubMerchId = request.getParameter("sub_mch_id");
		String strTotalFee = request.getParameter("total_fee");
		String strBody = CommonTool.transferCharactor(request.getParameter("body"), "ISO-8859-1", "UTF-8");
		String strOpenid = request.getParameter("openid");
		String strUserPayType = request.getParameter("user_pay_type");	// 1：扫码支付； 2： 公众号支付；3：刷卡支付；4：APP支付；5: H5支付；6：小程序支付
		
		System.out.println("strSubMerchId = " + strSubMerchId);
		System.out.println("strTotalFee = " + strTotalFee);
		System.out.println("strBody = " + strBody);
		System.out.println("strOpenid = " + strOpenid);
		System.out.println("strUserPayType = " + strUserPayType);
		
		boolean blnValidSumMch = super.validSubMchIsUsable(strSubMerchId);
		String strDispatcherURL = null;
		if (!blnValidSumMch) {	// 子商户无交易权限
			strDispatcherURL = "error.jsp?msg=商户无交易权限,请联系商户！";
		} else {	// 子商户具有交易权限
			Map<String, String> mapSendWxReqInfo = new HashMap<String, String>();
			mapSendWxReqInfo.put(PayOrderEntiry.SUB_MCH_ID, CommonTool.formatNullStrToSpace(strSubMerchId));
			mapSendWxReqInfo.put(PayOrderEntiry.TOTAL_FEE, CommonTool.formatYuanToCent(strTotalFee));
			mapSendWxReqInfo.put(PayOrderEntiry.FEE_TYPE, "USD");
			mapSendWxReqInfo.put(PayOrderEntiry.BODY, CommonTool.formatNullStrToSpace(strBody));
			mapSendWxReqInfo.put(PayOrderEntiry.OPEN_ID, CommonTool.formatNullStrToSpace(strOpenid));
			mapSendWxReqInfo.put(PayOrderEntiry.SPBILL_CREATE_IP, CommonTool.formatNullStrToSpace(CommonTool.getSpbill_Create_Ip()));
			mapSendWxReqInfo.put(PayOrderEntiry.TIME_EXPIRE, CommonTool.getBefOrAftFormatDate(new Date(), 60 * 60 * 1000 /*1小时*/, "yyyyMMddHHmmss"));
			mapSendWxReqInfo.put(PayOrderEntiry.NONCE_STR, CommonTool.getRandomString(32));
			mapSendWxReqInfo.put(PayOrderEntiry.OUT_TRADE_NO, CommonTool.getOutTradeNo(new Date(), 18));
			mapSendWxReqInfo.put(PayOrderEntiry.TRADE_TYPE, "JSAPI");
			mapSendWxReqInfo.put(PayOrderEntiry.NOTIFY_URL, CommonTool.getAbsolutWebURL(request, true) + "/" + CommonInfo.NOTIFY_URL_SERVLET);
			mapSendWxReqInfo = CommonTool.getAppendMap(mapSendWxReqInfo, CommonTool.getHarvestTransInfo());
			
			/** 插入由终端商户发起的初始化订单信息到数据库 **/
			Map<String, String> mapInitOrderInfo = CommonTool.getCloneMap(mapSendWxReqInfo);
			mapInitOrderInfo.put(PayOrderEntiry.USER_PAY_TYPE, strUserPayType);
			this.insertOrderInfoToTbl(mapInitOrderInfo);
			
			/** 向腾讯后台调用统一下单接口，并生成预付单 **/
			mapSendWxReqInfo.put(PayOrderEntiry.SIGN, CommonTool.getEntitySign(mapSendWxReqInfo));
			String strReqInfo = super.formatReqInfoToXML(mapSendWxReqInfo);
			System.out.println("strReqInfo = " + strReqInfo);
			String strWxRespInfo = super.sendReqAndGetResp(UNION_PAY_ORDER_URL, strReqInfo, CommonTool.getDefaultHttpClient());
			System.out.println("strWxRespInfo = " + strWxRespInfo);
			Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strWxRespInfo);
			
			/** 更新易付通后台与腾讯后台交互后的结果到对应的数据库表 **/
			Map<String, String> mapExtMapWxRespInfo = CommonTool.getCloneMap(mapWxRespInfo);
			mapExtMapWxRespInfo.put(PayOrderEntiry.OUT_TRADE_NO, mapSendWxReqInfo.get(PayOrderEntiry.OUT_TRADE_NO));
			this.updateOrderRstToTbl(mapExtMapWxRespInfo);
			
			/** 返回支付参数信息到终端商户 **/
			String strReturnCode = null;
			String strResultCode = null;
			if (mapWxRespInfo != null) {
				strReturnCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(PayOrderEntiry.RETURN_CODE));
				strResultCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(PayOrderEntiry.RESULT_CODE));
			}
			
//			strDispatcherURL = "../validPasswd.jsp?sub_mch_id=" + CommonTool.formatNullStrToSpace(strSubMerchId);
			strDispatcherURL = "validPasswd.jsp?sub_mch_id=" + CommonTool.formatNullStrToSpace(strSubMerchId);
			if (strReturnCode.equalsIgnoreCase(PayOrderEntiry.SUCCESS) && strResultCode.equalsIgnoreCase(PayOrderEntiry.SUCCESS)) {
				Map<String, String> mapJsAPIArgs = new HashMap<String, String>();
				mapJsAPIArgs.put("appId", mapWxRespInfo.get(PayOrderEntiry.APPID));
				mapJsAPIArgs.put("timeStamp", CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss"));
				mapJsAPIArgs.put("nonceStr", CommonTool.getRandomString(32));
				mapJsAPIArgs.put("package", "prepay_id=" + mapWxRespInfo.get(PayOrderEntiry.PREPAY_ID));
				mapJsAPIArgs.put("signType", "MD5");
				mapJsAPIArgs.put(PayOrderEntiry.APP_KEY, CommonInfo.NOB_KEY);
				mapJsAPIArgs.put("paySign", CommonTool.getEntitySign(mapJsAPIArgs));
				
				strDispatcherURL = strDispatcherURL.concat("&genPrepayOrderRst=").concat(PayOrderEntiry.SUCCESS);
				strDispatcherURL = strDispatcherURL.concat("&appId=").concat(mapJsAPIArgs.get("appId"));
				strDispatcherURL = strDispatcherURL.concat("&timeStamp=").concat(mapJsAPIArgs.get("timeStamp"));
				strDispatcherURL = strDispatcherURL.concat("&nonceStr=").concat(mapJsAPIArgs.get("nonceStr"));
				strDispatcherURL = strDispatcherURL.concat("&prepay_id=").concat(mapWxRespInfo.get(PayOrderEntiry.PREPAY_ID));
				strDispatcherURL = strDispatcherURL.concat("&signType=").concat(mapJsAPIArgs.get("signType"));
				strDispatcherURL = strDispatcherURL.concat("&paySign=").concat(mapJsAPIArgs.get("paySign"));
			} else if (strReturnCode.equalsIgnoreCase(PayOrderEntiry.FAIL)) {
				strDispatcherURL = strDispatcherURL.concat("&genPrepayOrderRst=").concat(PayOrderEntiry.FAIL)
									.concat("&msg=").concat(mapWxRespInfo.get(PayOrderEntiry.RETURN_MSG));
			} else if (strResultCode.equalsIgnoreCase(PayOrderEntiry.FAIL)) {
				strDispatcherURL = strDispatcherURL.concat("&genPrepayOrderRst=").concat(PayOrderEntiry.FAIL)
									.concat("&msg=").concat(mapWxRespInfo.get(PayOrderEntiry.ERR_CODE_DES));
			} else {
				strDispatcherURL = strDispatcherURL.concat("&genPrepayOrderRst=").concat(PayOrderEntiry.FAIL)
									.concat("&msg=数据有误，请联系管理员！");
			}
		}
		
		String strValidTotalFeeURL = CommonTool.getAbsolutWebURL(request, true) + "/" + strDispatcherURL;
		System.out.println("strValidTotalFeeURL = " + strValidTotalFeeURL);
		try {
			response.sendRedirect(strValidTotalFeeURL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		try {
//			System.out.println("strDispatcherURL = " + strDispatcherURL);
//			request.getRequestDispatcher(strDispatcherURL).forward(request, response);
//		} catch (ServletException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		this.doGet(request, response);
	}

	@Override
	public boolean insertOrderInfoToTbl(Map<String, String> mapArgs) {
		boolean blnUpRst = false;
		
		Connection conn = null;
		PreparedStatement prst = null;
		
		String strSubMchId = mapArgs.get(PayOrderEntiry.SUB_MCH_ID);
		String strOutTradeNo = mapArgs.get(PayOrderEntiry.OUT_TRADE_NO);
		System.out.println("strSubMchId = " + strSubMchId);
		System.out.println("strOutTradeNo = " + strOutTradeNo);
		if (strSubMchId == null || "".equals(strSubMchId) || strOutTradeNo == null || "".equals(strOutTradeNo)) {
			return blnUpRst;
		}
		
		try {
			conn = MysqlConnectionPool.getInstance().getConnection(false);
			
			// 更新易付通机构(商户)与子商户(终端商户)对应关系表
			String strSql = "replace into tbl_merch_submch(sub_mch_id, mch_id) values('" 
						+ mapArgs.get(PayOrderEntiry.SUB_MCH_ID)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.MCH_ID)
						+ "');";
			prst = conn.prepareStatement(strSql);
			prst.execute();
			prst.close();

			// 获取处于签到状态的店员ID
			Map<String, String> mapQuryArgs = new HashMap<String, String>();
			mapQuryArgs.put("sub_merchant_code", strSubMchId);
			String strTerminalMchId = this.getTblFieldValue("id", "t_merchant", mapQuryArgs);
			String strSignedAssitantId = super.getSignedAssitantid(strTerminalMchId);
			String strParsedAssistantId = CommonTool.formatNullStrToSpace(strSignedAssitantId);
					
			// 更新子商户与订单对应关系表
			strSql = "replace into tbl_submch_trans_order(sub_mch_id, out_trade_no, assistant_id) values('" 
						+ mapArgs.get(PayOrderEntiry.SUB_MCH_ID)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.OUT_TRADE_NO)
						+ "', '"
						+ (strParsedAssistantId.equals("") ? strSubMchId : strParsedAssistantId)
						+ "');";
			prst = conn.prepareStatement(strSql);
			prst.execute();
			prst.close();
			
			// 生成订单信息并将将其添加到订单信息表
			strSql = "replace into tbl_trans_order(out_trade_no, body, fee_type, total_fee, spbill_create_ip, time_start, time_expire, notify_url, trade_type, trade_state, openid, user_pay_type) values('" 
						+ mapArgs.get(PayOrderEntiry.OUT_TRADE_NO)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.BODY)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.FEE_TYPE)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.TOTAL_FEE)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.SPBILL_CREATE_IP)
						+ "', '" 
						+ CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss")
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.TIME_EXPIRE)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.NOTIFY_URL)
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.TRADE_TYPE)
						+ "', '" 
						+ PayOrderEntiry.NOTPAY
						+ "', '" 
						+ mapArgs.get(PayOrderEntiry.OPEN_ID)
						+ "', '"
						+ mapArgs.get(PayOrderEntiry.USER_PAY_TYPE)
						+ "');";
			prst = conn.prepareStatement(strSql);
			prst.execute();
			conn.commit();
			blnUpRst = true;
			
		} catch (SQLException e) {
			e.printStackTrace();
			MysqlConnectionPool.getInstance().rollback(conn);
			blnUpRst = false;
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
		}
		
		return blnUpRst;
	}

	@Override
	public boolean updateOrderRstToTbl(Map<String, String> mapArgs) {
		boolean blnUpRst = false;
		if (mapArgs == null || mapArgs.size() == 0) {
			return blnUpRst;
		}
		
		Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
		PreparedStatement prst = null;
		
		try {
			String strSql = "update tbl_trans_order set prepay_id='" 
							+ mapArgs.get(PayOrderEntiry.PREPAY_ID) 
							+ "' where out_trade_no='"
							+ mapArgs.get(PayOrderEntiry.OUT_TRADE_NO)
							+ "';";
			prst = conn.prepareStatement(strSql);
			prst.executeUpdate();
			conn.commit();
			
			blnUpRst = true;
		} catch(SQLException se) {
			se.printStackTrace();
			MysqlConnectionPool.getInstance().rollback(conn);
			blnUpRst = false;
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(prst, conn);
		}
		
		return blnUpRst;
	}
}
