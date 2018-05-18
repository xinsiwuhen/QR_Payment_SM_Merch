/**
 * @author xinwuhen
 */
package com.chinaepay.wx.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.entity.InquiryPayOrderEntity;
import com.chinaepay.wx.entity.InquiryRefundOrderEntity;
import com.chinaepay.wx.entity.PayOrderEntiry;
import com.chinaepay.wx.entity.RefundOrderEntity;

/**
 * @author xinwuhen
 *	本类主要用于测试。
 */
public class TransactionTester {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String strUrlWebRoot = "http://lmk.javang.cn/QRPaymentSM";
		TransactionTester transTester = new TransactionTester();
		
		/** 查询结算资金 **/
//		try {
//			// 装填参数
//			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
//			nvps.add(new BasicNameValuePair("startTimeForSettleOrder", "20180403"));
//			nvps.add(new BasicNameValuePair("endTimeForSettleOrder", "20180502"));
//			String strURL = "http://localhost:8080/QR_Payment/payment/downloadSettleOrderServlet";
//			transTester.sendReqAndGetRespInfo(strURL, nvps);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		/** 测试生成QR图片并入库 **/
//		try {
//			transTester.genQRCodeAndUpToTbl();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		
		/** 测试支付订单 **/
//		strBizType = CommonInfo.PAYMENT_TRANSACTION_BIZ;
//		strBizReq = transTester.getPamentTransRequest();
//		exeSocketTest(strBizType + ":" + strBizReq);
		
		/** 测试查询订单 **/
//		strBizType = CommonInfo.INQUIRY_TRANSACTION_BIZ;
//		strBizReq = transTester.getInquiryTransRequest();
//		exeSocketTest(strBizType + ":" + strBizReq);
		
		/** 测试撤销订单 **/
//		strBizType = CommonInfo.REVERSE_TRANSACTION_BIZ;
//		strBizReq = transTester.getReverseTransRequest();
//		exeSocketTest(strBizType + ":" + strBizReq);
		
		/** 测试退款订单 **/
		try {
			// 装填参数
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("out_trade_no", "20180517131729420390543138188002"));
			nvps.add(new BasicNameValuePair("refund_fee", "1"));
			nvps.add(new BasicNameValuePair("refund_desc", "Refund For Kevin."));
			String strURL = strUrlWebRoot + "/payment/refundOrderServlet";
			transTester.sendReqAndGetRespInfo(strURL, nvps);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		/** 测试查询退款单 **/
//		strBizType = CommonInfo.INQUIRY_REFUND_BIZ;
//		strBizReq = transTester.getInquiryRefundRequest();
//		exeSocketTest(strBizType + ":" + strBizReq);
		
	}

	private void sendReqAndGetRespInfo(String strUrl, List<NameValuePair> nvps) throws IOException {
		String body = "";
		// 创建httpclient对象
		CloseableHttpClient client = HttpClients.createDefault();
		// 创建post方式请求对象
		HttpPost httpPost = new HttpPost(strUrl); // 设置响应头信息

		// 设置参数到请求对象中
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

		System.out.println("请求地址：" + httpPost.getURI().toURL());
		System.out.println("请求参数：" + nvps.toString());

		// 设置header信息
		// 指定报文头【Content-type】、【User-Agent】
		httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
		httpPost.setHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

		// 执行请求操作，并拿到结果（同步阻塞）
		CloseableHttpResponse response = client.execute(httpPost);
		
		int intStatusCode = response.getStatusLine().getStatusCode();
		String strReasonPhrase = response.getStatusLine().getReasonPhrase();
		
		System.out.println("intStatusCode = " + intStatusCode);
		System.out.println("strReasonPhrase = " + strReasonPhrase);
		
		if(intStatusCode == HttpStatus.SC_OK && "ok".equalsIgnoreCase(strReasonPhrase)) {
			// 获取结果实体
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				// 按指定编码转换结果实体为String类型
				body = EntityUtils.toString(entity, "UTF-8");
			}
			EntityUtils.consume(entity);

			System.out.println("body = " + body);
		}
		
		// 释放链接
		response.close();
	}

	private void genQRCodeAndUpToTbl() throws IOException {

		String body = "";

		// 创建httpclient对象
		CloseableHttpClient client = HttpClients.createDefault();
		// 创建post方式请求对象
		HttpPost httpPost = new HttpPost("http://localhost:8080/QR_Payment/qrcode/genQrCodeSvlt"); // 设置响应头信息

		// 装填参数
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("sub_mch_id", "12152566"));

		// 设置参数到请求对象中
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

		System.out.println("请求地址：" + httpPost.getURI().toURL());
		System.out.println("请求参数：" + nvps.toString());

		// 设置header信息
		// 指定报文头【Content-type】、【User-Agent】
		httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
		httpPost.setHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

		// 执行请求操作，并拿到结果（同步阻塞）
		CloseableHttpResponse response = client.execute(httpPost);
		
		int intStatusCode = response.getStatusLine().getStatusCode();
		String strReasonPhrase = response.getStatusLine().getReasonPhrase();
		
		System.out.println("intStatusCode = " + intStatusCode);
		System.out.println("strReasonPhrase = " + strReasonPhrase);
		
		if(intStatusCode == HttpStatus.SC_OK && "ok".equalsIgnoreCase(strReasonPhrase)) {
			// 获取结果实体
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				// 按指定编码转换结果实体为String类型
				body = EntityUtils.toString(entity, "UTF-8");
			}
			EntityUtils.consume(entity);

			System.out.println("body = " + body);
		}
		
		// 释放链接
		response.close();
	}
	
	/**
	 * 支付交易对应的请求报文，格式：appid=43453&mch_id=dsw342&sub_mch_id=983477232&nonce_str=aiadjsis8732487jsd8l
	 * @return
	 */
	private String getPamentTransRequest() {
		
		String strAuthCode = "135029600960750624"; // 二维码中的用户授权码
		StringBuffer sb = new StringBuffer();
		sb.append(PayOrderEntiry.AGENT_ID + "=" + "1r10s84408mdj0tgp6iov2c0k54jbps9");
		sb.append("&" + PayOrderEntiry.SUB_MCH_ID + "=" + "12152566");
		sb.append("&" + PayOrderEntiry.NONCE_STR + "=" + CommonTool.getRandomString(32));
		sb.append("&" + PayOrderEntiry.BODY + "=" + "Ipad mini  16G  白色"); // Ipad mini  16G  白色
		sb.append("&" + PayOrderEntiry.OUT_TRADE_NO + "=" + CommonTool.getOutTradeNo(new Date(), 18));
		sb.append("&" + PayOrderEntiry.TOTAL_FEE + "=" + "1");
		sb.append("&" + PayOrderEntiry.FEE_TYPE + "=" + "USD");
		sb.append("&" + PayOrderEntiry.SPBILL_CREATE_IP + "=" + CommonTool.getSpbill_Create_Ip());
		sb.append("&" + PayOrderEntiry.AUTH_CODE + "=" + strAuthCode);
//		sb.append("&" + PaymentTransactionEntity.APP_KEY + "=" + "024edfffae32c829b012c98a61686f3b");
		
		return sb.toString();
	}
	
	/**
	 * 撤销单请求报文。
	 * @return
	 */
//	private String getReverseTransRequest() {
//		StringBuffer sb = new StringBuffer();
//		sb.append(ReverseTransactionEntity.AGENT_ID + "=" + "1r10s84408mdj0tgp6iov2c0k54jbps9");
//		sb.append("&" + ReverseTransactionEntity.SUB_MCH_ID + "=" + "12152566");
//		sb.append("&" + ReverseTransactionEntity.OUT_TRADE_NO + "=" + "20180317162341003734102708751406"); // 测试时修改此字段
//		sb.append("&" + ReverseTransactionEntity.NONCE_STR + "=" + CommonTool.getRandomString(32));
////		sb.append("&" + ReverseTransactionEntity.APP_KEY + "=" + "024edfffae32c829b012c98a61686f3b");
//		
//		return sb.toString();
//	}
	
	/**
	 * 退款请求报文。
	 * @return
	 */
	private String getRefundTransRequest() {
		StringBuffer sb = new StringBuffer();
		sb.append(RefundOrderEntity.AGENT_ID + "=" + "1r10s84408mdj0tgp6iov2c0k54jbps9");
		sb.append("&" + RefundOrderEntity.SUB_MCH_ID + "=" + "12152566");
		sb.append("&" + RefundOrderEntity.NONCE_STR + "=" + CommonTool.getRandomString(32));
		sb.append("&" + RefundOrderEntity.OUT_TRADE_NO + "=" + "20180317173710924965776167374654"); // 测试时修改此字段
		sb.append("&" + RefundOrderEntity.OUT_REFUND_NO + "=" + CommonTool.getOutRefundNo(new Date(), 18));	// 同一退款单号时记得修改此字段为固定值
		sb.append("&" + RefundOrderEntity.TOTAL_FEE + "=" + "1");
		sb.append("&" + RefundOrderEntity.REFUND_FEE + "=" + "1");
//		sb.append("&" + RefundTransactionEntity.APP_KEY + "=" + "024edfffae32c829b012c98a61686f3b");
		
		return sb.toString();
	}
	
	/**
	 * 生成查询操作所需的参数列表(HashMap类型).
	 * @param hmTransactionOrderCont
	 * @return
	 */
	private String getInquiryTransRequest() {
		StringBuffer sb = new StringBuffer();
		sb.append(InquiryPayOrderEntity.AGENT_ID + "=" + "1r10s84408mdj0tgp6iov2c0k54jbps9");
		sb.append("&" + InquiryPayOrderEntity.SUB_MCH_ID + "=" + "12152566");
		sb.append("&" + InquiryPayOrderEntity.OUT_TRADE_NO + "=" + "20180317173710924965776167374654");	// 测试时修改此参数
		sb.append("&" + InquiryPayOrderEntity.NONCE_STR + "=" + CommonTool.getRandomString(32));
//		sb.append("&" + InquiryTransactionEntity.APP_KEY + "=" + "024edfffae32c829b012c98a61686f3b");
		
		return sb.toString();
	}
	
	/**
	 * 查询退款单对应的请求报文。
	 * @return
	 */
	private String getInquiryRefundRequest() {
		StringBuffer sb = new StringBuffer();
		sb.append(InquiryRefundOrderEntity.AGENT_ID + "=" + "1r10s84408mdj0tgp6iov2c0k54jbps9");
		sb.append("&" + InquiryRefundOrderEntity.SUB_MCH_ID + "=" + "12152566");
		sb.append("&" + InquiryRefundOrderEntity.NONCE_STR + "=" + CommonTool.getRandomString(32));
		sb.append("&" + InquiryRefundOrderEntity.OUT_TRADE_NO + "=" + "20180317173710924965776167374654");	// 测试时需修改此参数
//		sb.append("&" + InquiryRefundEntity.APP_KEY + "=" + "024edfffae32c829b012c98a61686f3b");
		
		return sb.toString();
	}
}
