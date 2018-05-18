package com.chinaepay.wx.servlet;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;

import net.sf.json.JSONObject;

/**
 * 获取微信的OpenId，并打开需要用户输入支付金额的页面。
 * @author xinwuhen
 */
public class GetWxOpenIdServlet extends TransControllerServlet {
	private static final String URL_OAUTH_OPENID = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code";	
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		
		String strSubMchId = request.getParameter("sub_mch_id");
		String strAuthCode = request.getParameter("code");
		System.out.println("strSubMchId = " + strSubMchId);
		System.out.println("strAuthCode = " + strAuthCode);
		
		String strGetWxOpenIdURL = URL_OAUTH_OPENID.replace("APPID", CommonInfo.NOB_APP_ID).replace("SECRET", CommonInfo.NOB_APP_SECRET).replace("CODE", strAuthCode);
		String strJsonRespFromWx = super.sendReqAndGetResp(strGetWxOpenIdURL, "", CommonTool.getDefaultHttpClient());
		System.out.println("strJsonRespFromWx = " + strJsonRespFromWx);
		
//		JSONObject jsonObj = JSONObject.fromObject(strJsonRespFromWx);
//		String strOpenId = jsonObj.getString("openid");
		
		String strOpenId =  this.getOpenIdFromWxRespInfo(strJsonRespFromWx);
		String strDispatcherURL = null;
		if (strOpenId != null) {
//			strDispatcherURL = "../validTotalFee.jsp?sub_mch_id=" + strSubMchId + "&openid=" + strOpenId;
			strDispatcherURL = "validTotalFee.jsp?sub_mch_id=" + strSubMchId + "&openid=" + strOpenId;
		} else {
//			strDispatcherURL = "../error.jsp?msg=获取顾客的OpenId失败！";
			strDispatcherURL = "error.jsp?sub_mch_id=" + strSubMchId + "&msg=获取顾客的OpenId失败！";
		}
		System.out.println("strDispatcherURL = " + strDispatcherURL);
		
		
		String strValidTotalFeeURL = CommonTool.getAbsolutWebURL(request, true) + "/" + strDispatcherURL;
		try {
			response.sendRedirect(strValidTotalFeeURL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		try {
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
	
	/**
	 * 从微信端返回的Access_token中获取openId。
	 * @param strJsonRespFromWx
	 * @return
	 */
	private String getOpenIdFromWxRespInfo(String strJsonRespFromWx) {
		String strOpenId = null;
		if (strJsonRespFromWx != null && !strJsonRespFromWx.contains("errcode") && strJsonRespFromWx.contains("openid")) {
			JSONObject jsonObj = JSONObject.fromObject(strJsonRespFromWx);
			strOpenId = jsonObj.getString("openid");
			/*
			strJsonRespFromWx = strJsonRespFromWx.replace("{", "").replace("}", "").replace("\"", "");
			String[] strOutters = strJsonRespFromWx.split(",");
			for (String strOutter : strOutters) {
				String[] strInners = strOutter.split(":");
				if ("openid".equalsIgnoreCase(strInners[0])) {
					strOpenId = strInners[1];
				}
			}
			*/
		}
		
		return strOpenId;
	}

	@Override
	public boolean insertOrderInfoToTbl(Map<String, String> mapArgs) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean updateOrderRstToTbl(Map<String, String> mapArgs) {
		// TODO Auto-generated method stub
		return true;
	}
}
