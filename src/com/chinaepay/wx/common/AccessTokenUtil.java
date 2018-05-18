package com.chinaepay.wx.common;

import java.util.Date;

import com.chinaepay.wx.servlet.ExtendsHttpServlet;

import net.sf.json.JSONObject;

/**
 * 获取AccessToken类，在发送模板消息时需要用到AccessToken。 发送模板消息URL:
 * https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=
 * ACCESS_TOKEN http请求方式: POST
 * 
 * @author xinwuhen
 */
public class AccessTokenUtil extends ExtendsHttpServlet {
	private static final String WX_ACCESS_TOKEN_GET_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=APPSECRET";
	private static AccessTokenUtil accessTokenUtil = null;
	private AccessToken accessToken = null;

	private AccessTokenUtil() {}

	public static AccessTokenUtil getInstance() {
		if (accessTokenUtil == null) {
			accessTokenUtil = new AccessTokenUtil();
		}
		return accessTokenUtil;
	}

	/**
	 * 获取AccessToken对象。
	 * 
	 * @return
	 */
	public AccessToken getAccessTokenObj() {
		// 判断当前AccessToken对象是否有效
		boolean blnIsValiable = this.isVilableToken(accessToken);
		if (!blnIsValiable) {	// 当前存储的Token已经失效，需要重新从腾讯后台获取
			accessToken = getTokenFromTencent();
		}
		
		return accessToken;
	}
	
	/**
	 * 判断当前AccessToken对象是否有效。
	 * @param accessToken
	 * @return
	 */
	private boolean isVilableToken(AccessToken accessToken) {
		boolean blnValidTokenObj = false;
		
		if (accessToken != null) {
			String strTokenValue = accessToken.getTokenValue();
			long lngExpireTime = accessToken.getExpiresTime();
			long lngCurrentTime = new Date().getTime();
			
			if (strTokenValue != null && !"".equals(strTokenValue)	// 校验Token字符串是否为空
					&& lngCurrentTime < lngExpireTime) {	// 校验Token是否过期
				blnValidTokenObj = true;
			}
		}
		
		return blnValidTokenObj;
	}
	
	/**
	 * 从腾讯侧获取AccessToken对象。
	 * @return
	 */
	private AccessToken getTokenFromTencent() {
		AccessToken accessToken = null;
		String strAccessTokenURL = WX_ACCESS_TOKEN_GET_URL.replaceFirst("APPID", CommonInfo.NOB_APP_ID).replaceFirst("APPSECRET", CommonInfo.NOB_APP_SECRET);
		
		String strJsonRespFromWx = sendReqAndGetResp(strAccessTokenURL, "", CommonTool.getDefaultHttpClient());
		if (strJsonRespFromWx != null && !strJsonRespFromWx.contains("errcode")) {
			JSONObject jsonObj = JSONObject.fromObject(strJsonRespFromWx);
			String strAccessToken = jsonObj.getString("access_token");
			String strExpiresIn = jsonObj.getString("expires_in");
			
			if (strAccessToken != null && !"".equals(strAccessToken) && strExpiresIn != null && !"".equals(strExpiresIn)) {
				// 将当前时间加上失效时间后，再向前推10分钟。如： 失效时间是2小时，则以下方式计算后失效时间变为当前时间之后的1小时50分。
				long lngExpireTime = new Date().getTime() + Long.parseLong(strExpiresIn) - 10 * 60 * 1000;
				accessToken = new AccessToken(strAccessToken, lngExpireTime);
			}
		}
		
		return accessToken;
	}

	/**
	 * AccessToken封装类。
	 * 
	 * @author xinwuhen
	 */
	public class AccessToken {
		// 接口访问凭证
		private String tokenValue = null;

		// 凭证失效时间
		private long expiresTime = 0L;

		private AccessToken(){}
		
		public AccessToken(String tokenValue, long expiresTime) {
			this.tokenValue = tokenValue;
			this.expiresTime = expiresTime;
		}
		
		public String getTokenValue() {
			return tokenValue;
		}
		
		public long getExpiresTime() {
			return expiresTime;
		}
	}
}
