package com.chinaepay.wx.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;

/**
 * 在获取支付用户的OpenId之前，需要获取授权码Code.
 * 
 * @author xinwuhen
 */
public class GetWxAuthCodeServlet extends TransControllerServlet {
	private static final String URL_OAUTH_AUTHORIZE = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=APPID&redirect_uri=REDIRECTURI&response_type=code&scope=snsapi_base&state=STATE#wechat_redirect"; 
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		
		String strSubMchId = request.getParameter("sub_mch_id");
		
		String redirectURL = CommonTool.getAbsolutWebURL(request, false) + "/" + CommonInfo.GET_WX_OPEN_ID_URL + "?sub_mch_id=" + strSubMchId;
		System.out.println("redirectURL = " + redirectURL);
		try {
			redirectURL = URLEncoder.encode(redirectURL, "UTF-8");
			String strGetWxOpenIdURL = URL_OAUTH_AUTHORIZE.replace("APPID", CommonInfo.NOB_APP_ID).replace("REDIRECTURI", redirectURL).replace("STATE", CommonTool.getRandomString(16));
			System.out.println("strGetWxOpenIdURL = " + strGetWxOpenIdURL);
			
			response.sendRedirect(strGetWxOpenIdURL);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		this.doGet(request, response);
	}

	@Override
	public boolean insertOrderInfoToTbl(Map<String, String> mapArgs) {
		return true;
	}

	@Override
	public boolean updateOrderRstToTbl(Map<String, String> mapArgs) {
		return true;
	}
}
