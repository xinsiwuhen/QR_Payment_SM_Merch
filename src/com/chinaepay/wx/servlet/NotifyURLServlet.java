package com.chinaepay.wx.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.AccessTokenUtil;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.NotifyURLEntity;
import com.chinaepay.wx.entity.PayOrderEntiry;

import net.sf.json.JSONObject;

/**
 * 交易单支付后，处理腾讯后台发送来的异步通知。
 * @author xinwuhen
 */
public class NotifyURLServlet extends TransControllerServlet {

	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		String strReqInfo = null;
		InputStream is = null;
		try {
			is = request.getInputStream();
			strReqInfo = CommonTool.getInputStreamInfo(is, "UTF-8");
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (strReqInfo != null) {
			System.out.println("strReqInfo = " + strReqInfo);
			Map<String, String> mapWxRespInfo = new ParsingWXResponseXML().formatWechatXMLRespToMap(strReqInfo);
			System.out.println("mapWxRespInfo = " + mapWxRespInfo);
			
			/** 校验之前是否处理过交易。若未处理过,则对交易单标识为“已处理”，同时返回应答报文给腾讯端（告知腾讯继续或停止发送支付通知） **/
			String strRespXMLInfo = null;
			String strReturnCode = CommonTool.formatNullStrToSpace(mapWxRespInfo.get(NotifyURLEntity.RETURN_CODE));
			if (!strReturnCode.equals(NotifyURLEntity.SUCCESS)) {	// ReturnCode返回Null或是数值不是Sucess时，代表支付通知发送到易付通支付平台失败，需要再次等待支付通知
				strRespXMLInfo = "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
			} else {
				strRespXMLInfo = "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
				
				// 校验交易单是否处理过此类回调通知（true: 处理过； false：未处理过。）
				String strOutTradeNo = mapWxRespInfo.get(NotifyURLEntity.OUT_TRADE_NO);
				if (strOutTradeNo != null) {
					boolean blnValidNotifyProcRst = validNotifyProcResult(strOutTradeNo);
					// 若交易单回调通知未被处理过(false)，则将支付结果更新到支付订单表(tbl_trans_order) 
					if (!blnValidNotifyProcRst) {
						boolean blnUpTransOrderRst = updateOrderRstToTbl(mapWxRespInfo);
						
						// 若更新支付订单表成功，则向绑定公众号的店员及商户分别发送订单支付成功的通知
						if (blnUpTransOrderRst) {
							String strResultCode = mapWxRespInfo.get(NotifyURLEntity.RESULT_CODE);
							if (strResultCode != null && strResultCode.equals(NotifyURLEntity.SUCCESS)) {	// 支付成功
								// 发送消息通知给终端商户内的值班店员
								String strNoticeResp = new PaymentTemplateNotice().sendPaymentNotice(strOutTradeNo);
								System.out.println(">>>strNoticeResp = " + strNoticeResp);
							}
						}
					}
				}
			}
			
			PrintWriter pw = null;
			try {
				pw = response.getWriter();
				pw.write(strRespXMLInfo);
				pw.flush();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (pw !=null) {
					pw.close();
				}
			}
		}
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		this.doGet(request, response);
	}
	
	/**
	 * 校验订单是否处理过此类回调通知【true: 处理过； false：未处理过】。
	 * @param strOutTradeNo
	 * @return
	 */
	private boolean validNotifyProcResult(String strOutTradeNo) {
		Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
		PreparedStatement prst = null;
		ResultSet rs = null;
		boolean blnNtyPrcRst = false;
		String strInquirySql = "select * from tbl_trans_order where out_trade_no='" + strOutTradeNo + "';";
		try {
			prst = conn.prepareStatement(strInquirySql);
			rs = prst.executeQuery();
			if (rs.next()) {
				String strNtyProcRst = rs.getString("notify_proc_rst");
				if (strNtyProcRst == null || !"Y".equals(strNtyProcRst)) {
					blnNtyPrcRst = false;
				} else {
					blnNtyPrcRst = true;
				}
			}
		} catch(SQLException se) {
			se.printStackTrace();
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
		}
		
		return blnNtyPrcRst;
	}
	
	@Override
	public boolean insertOrderInfoToTbl(Map<String, String> mapArgs) {
		return true;
	}

	@Override
	public boolean updateOrderRstToTbl(Map<String, String> mapArgs) {
		boolean blnUpRst = false;
		if (mapArgs == null || mapArgs.size() == 0) {
			return blnUpRst;
		}
		
		String strReturnCode = mapArgs.get(NotifyURLEntity.RETURN_CODE);
		if (strReturnCode != null && strReturnCode.equals(NotifyURLEntity.SUCCESS)) {
			Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
			PreparedStatement prst = null;
			String strResultCode = mapArgs.get(NotifyURLEntity.RESULT_CODE);
			String strStateDesc = "";
			if (strResultCode.equalsIgnoreCase(NotifyURLEntity.SUCCESS)) {
				strStateDesc = "支付成功";
			} else {
				strStateDesc = "支付失败";
			}
			String strUpdateSql = "update tbl_trans_order set notify_proc_rst='Y', transaction_id='" + CommonTool.formatNullStrToSpace(mapArgs.get(NotifyURLEntity.TRANSACTION_ID)) 
									+ "', time_end='" + CommonTool.formatNullStrToSpace(mapArgs.get(NotifyURLEntity.TIME_END))
									+ "', bank_type='" + CommonTool.formatNullStrToSpace(mapArgs.get(NotifyURLEntity.BANK_TYPE)) 
									+ "', cash_fee='" + CommonTool.formatNullStrToSpace(mapArgs.get(NotifyURLEntity.CASH_FEE)) 
									+ "', trade_state='" + strResultCode
									+ "', trade_state_desc = '" + strStateDesc
									+ "' where out_trade_no='" + CommonTool.formatNullStrToSpace(mapArgs.get(NotifyURLEntity.OUT_TRADE_NO)) + "';";
			System.out.println("strUpdateSql = " + strUpdateSql);
			try {
				prst = conn.prepareStatement(strUpdateSql);
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
		}
		
		return blnUpRst;
	}
	
	/**
	 * 顾客支付成功后，Harvest后台会收到腾讯返回的回调通知；当回调通知以前没有被处理过，则会再次进行处理。
	 * 回调通知处理完成后，Harvest后台会依据公众号内的消息模板向终端商户、店员发送支付消息。
	 * @author xinwuhen
	 */
	public class PaymentTemplateNotice {
		private static final String SEND_TEMPLATE_NOTICE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=ACCESS_TOKEN";
		
		/**
		 * 发送模板消息通知。
		 * @param strOutTradeNo
		 */
		public String sendPaymentNotice(String strOutTradeNo) {
			String strNoticeResp = null;
			if (strOutTradeNo == null || "".equals(strOutTradeNo)) {
				return strNoticeResp;
			}
			
			String strAccessToken = AccessTokenUtil.getInstance().getAccessTokenObj().getTokenValue();
			System.out.println("strAccessToken = " + strAccessToken);
			if (strAccessToken != null && !"".equals(strAccessToken)) {
				WxNoticeTemplate wxNoticeTemp = getWxNoticeTemplate(strOutTradeNo);
				System.out.println("wxNoticeTemp = " + wxNoticeTemp);
				if (wxNoticeTemp != null) {
					String strJsonNoticeInfo = JSONObject.fromObject(wxNoticeTemp).toString();
					System.out.println("strJsonNoticeInfo = " + strJsonNoticeInfo);
					
					String strSendTempNoticeURL = SEND_TEMPLATE_NOTICE_URL.replaceFirst("ACCESS_TOKEN", strAccessToken);
					System.out.println("strSendTempNoticeURL = " + strSendTempNoticeURL);
					
					// 向商户发送模板通知信息
					strNoticeResp = sendReqAndGetResp(strSendTempNoticeURL, strJsonNoticeInfo, CommonTool.getDefaultHttpClient());
				}
			}
			
			return strNoticeResp;
		}
		
		/**
		 * 生成消息通知的模板，并封装相关的数据。
		 * @param strOutTradeNo
		 * @return
		 */
		private WxNoticeTemplate getWxNoticeTemplate(String strOutTradeNo) {
			WxNoticeTemplate wxNoticeTemp = new WxNoticeTemplate();
			
			// 获取此交易单所对应的签到店员ID
			Map<String, String> mapArgs = new HashMap<String, String>();
			mapArgs.put("out_trade_no", strOutTradeNo);
			String strAssistantId = getTblFieldValue("assistant_id", "tbl_submch_trans_order", mapArgs);
			System.out.println("strAssistantId = " + strAssistantId);
			
			if (strAssistantId != null) {
				// 获取接收者openid
				mapArgs.clear();
				mapArgs.put("relation_id", strAssistantId);
				mapArgs.put("user_type", "3");	// 1代理 2 商户 3 店员
				String strAssisOpenId = getTblFieldValue("open_id", "t_wechat_user", mapArgs);
				System.out.println("strAssisOpenId = " + strAssisOpenId);
				
				// 获取微信昵称
				String strWxNickName = getTblFieldValue("nick_name", "t_wechat_user", mapArgs);
				System.out.println("strWxNickName = " + strWxNickName);
				
				// 获取交易金额
				mapArgs.clear();
				mapArgs.put("out_trade_no", strOutTradeNo);
				String strTotalFee = getTblFieldValue("total_fee", "tbl_trans_order", mapArgs);
				strTotalFee = CommonTool.formatNullStrToZero(strTotalFee);
				strTotalFee = CommonTool.formatDoubleToHalfUp(Double.parseDouble(strTotalFee) / 100d, 2, 2) + " USD";
				System.out.println("strTotalFee = " + strTotalFee);
				
				// 获取支付方式
				String strPayType = getTblFieldValue("user_pay_type", "tbl_trans_order", mapArgs);
				if (strPayType != null && strPayType.equals(PayOrderEntiry.PAY_TYPE_SCAN_QR)) {
					strPayType = "扫码支付";
				} else {
					strPayType = "扫码支付";
				}
				
				// 获取交易时间
				String strTimeEnd = getTblFieldValue("time_end", "tbl_trans_order", mapArgs);
				Date dateTimeEnd = CommonTool.getDateBaseOnChars("yyyyMMddHHmmss", strTimeEnd);
				strTimeEnd = CommonTool.getFormatDateStr(dateTimeEnd, "yyyy-MM-dd HH:mm:ss");
				System.out.println("strTimeEnd = " + strTimeEnd);
				
				// 封装模板数据
				Map<String, TemplateData> data = new HashMap<String, TemplateData>();
				TemplateData first = new TemplateData();
				first.setValue("您好，顾客[" + CommonTool.formatNullStrToSpace(strWxNickName) + "]完成一笔支付，详情如下:");
				TemplateData keyword1 = new TemplateData();	// 交易金额
				keyword1.setValue(strTotalFee);
				TemplateData keyword2 = new TemplateData();	// 支付方式
				keyword2.setValue(strPayType);
				TemplateData keyword3 = new TemplateData();	// 交易时间
				keyword3.setValue(strTimeEnd);
				TemplateData keyword4 = new TemplateData();	// 交易单号(注：此处为商户订单号)
				keyword4.setValue(strOutTradeNo);
				TemplateData remark = new TemplateData();
				remark.setValue("请确认以上信息是否正确.");
				
				data.put("first", first);
				data.put("keyword1", keyword1);
				data.put("keyword2", keyword2);
				data.put("keyword3", keyword3);
				data.put("keyword4", keyword4);
				data.put("remark", remark);
				
				// 将以上数据封装到模板中
				wxNoticeTemp.setTouser(strAssisOpenId);
				wxNoticeTemp.setTemplate_id(CommonInfo.PAYMENT_NOTICE_TEMPLATE_ID);
				wxNoticeTemp.setData(data);
			}
			
			return wxNoticeTemp;
		}
		
		/**
		 * 消息模板封装类。
		 * 微信官方文档参考URL：https://mp.weixin.qq.com/wiki?t=resource/res_main&id=mp1433751277
		 * 1、模板消息调用时主要需要模板ID和模板中各参数的赋值内容；
		 * 2、模板中参数内容必须以".DATA"结尾，否则视为保留字；
		 * 3、模板保留符号"{{ }}"。
		 * 
		 * 【举例】：  
		 * {{first.DATA}}
		 * 交易金额：{{keyword1.DATA}}  
		 * 支付方式：{{keyword2.DATA}}  
		 * 交易时间：{{keyword3.DATA}}
		 * 交易单号：{{keyword4.DATA}}
		 * {{remark.DATA}}
		 * 
		 * @author xinwuhen
		 */
		public class WxNoticeTemplate {
			// 接收者openid
			private String touser = null;
			// 模板ID
			private String template_id = null;
			// 模板数据
			private Map<String, TemplateData> data = null;

			public String getTouser() {
				return touser;
			}

			public void setTouser(String touser) {
				this.touser = touser;
			}

			public String getTemplate_id() {
				return template_id;
			}

			public void setTemplate_id(String template_id) {
				this.template_id = template_id;
			}

			public Map<String, TemplateData> getData() {
				return data;
			}

			public void setData(Map<String, TemplateData> data) {
				this.data = data;
			}
		}
		
		/**
		 * 消息模板中的数据。
		 * @author xinwuhen
		 */
		public class TemplateData {
			private String value = null;
			private String color = null;

			public String getValue() {
				return value;
			}

			public void setValue(String value) {
				this.value = value;
			}

			public String getColor() {
				return color;
			}

			public void setColor(String color) {
				this.color = color;
			}
		}
	} 
}
