/**
 * @author xinwuhen
 */
package com.chinaepay.wx.common;

/**
 * @author xinwuhen
 * 本类主要存储固定的配置参数信息，如：微信支付URL、JDBC驱动类名、公众账号ID等
 */
public class CommonInfo {
	/** 腾讯服务器域名地址 **/
	// 中国香港
	public static final String HONG_KONG_CN_SERVER_URL = "https://apihk.mch.weixin.qq.com";
	// 中国大陆
	public static final String MAIN_LAND_CN_SERVER_URL = "https://api.mch.weixin.qq.com";
	
	// 用户扫描二维码, 在获取用户的OpenId之前, 需获取CODE码
	public static final String GET_WX_AUTH_CODE_URL = "payment/getWxAuthCodeServlet";
	// 获取微信的OpenId，并打开需要用户输入支付金额的页面
	public static final String GET_WX_OPEN_ID_URL = "payment/getWxOpenIdServlet";
	
	// 订单支付后的回调URL(Servlet名称，需参照web.xml进行配置)
	public static final String NOTIFY_URL_SERVLET = "payment/notifyURLServlet";
	
	/** 文件存放目录 **/
	// 二维码文件目录
	public static final String QR_IMG_FOLDER = "/upload/harvpay/qr_img";
	// NOB上传的对账单返回文件存放目录
	public static final String SETTLE_FILE_FOLDER_FOR_NOB_UP = "/upload/harvpay/stl_nob_up";
	// 为NOB下载而生成的对账单文件存放目录
	public static final String SETTLE_FILE_FOLDER_FOR_NOB_DOWN = "/upload/harvpay/stl_nob_down";
	
	/** NOB交易相关的基本信息(由腾讯分派) **/
	// 公众账号ID
	public static final String NOB_APP_ID = "wxd1be3a5544867c03";
	// 公众号密钥
	public static final String NOB_APP_SECRET = "680228e663f997100af02a70813cabf5";
	// 商户号
	public static final String NOB_MCH_ID = "1494500362";
	// 密钥
	public static final String NOB_KEY = "ChinaepayUSA17029371969000000000";
	// 商户证书密码（密码默认为您的商户ID）
	public static String SSL_CERT_PASSWORD = "1494500362";
	// 向终端商户发送消息通知时的消息模板ID
	public static String PAYMENT_NOTICE_TEMPLATE_ID = "k9fBz37IC-jfDx4RGFboVL61i8s9ORxfBZ4seQVAGfA";
	
	/** Harvest唯一的ID，此处为随机设置的数字 **/
	public static final String HARVEST_ID = "HARVEST";
	
	// 24小时所对应的毫秒数
	public static final long ONE_DAY_TIME = 24 * 60 * 60 * 1000L;
}
