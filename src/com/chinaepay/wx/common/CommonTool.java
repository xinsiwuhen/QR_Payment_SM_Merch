/**
 * @author xinwuhen
 */
package com.chinaepay.wx.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.chinaepay.wx.entity.CommunicateEntity;
import com.chinaepay.wx.entity.PayOrderEntiry;

/**
 * @author xinwuhen 交易过程中所需参数的计算工具类。
 */
public class CommonTool {
	/**
	 * 将金额以“元”单位转化为“分”。
	 * @param strAmount
	 * @return
	 */
	public static String formatYuanToCent(String strAmount) {
		strAmount = (strAmount == null || "".equals(strAmount)) ? "0" : strAmount;
		double dblAmount = Double.parseDouble(strAmount);
		return String.valueOf((int) (dblAmount * 100));
	}
	
	/**
	 * 将金额单位“分”转化为“元”。
	 * @param strAmount
	 * @return
	 */
	public static String formatCentToYuan(String strAmount, int iPointNum) {
		strAmount = (strAmount == null || "".equals(strAmount)) ? "0" : strAmount;
		double dblYuan = Double.parseDouble(strAmount) / 100d;
		
		return CommonTool.formatDoubleToHalfUp(dblYuan, iPointNum, iPointNum);
	}
	
	/**
	 * 在原字符串前边补充指定的字符，并保证总字符串长度。
	 * @param strSrcChars
	 * @param strPreChar
	 * @param iTotalLength
	 * @return
	 */
	public static String getFixLenStr(String strSrcChars, String strPreChar, int iTotalLength) {
		if (strSrcChars == null || strPreChar == null || iTotalLength <= 0) {
			return "";
		}
		
		String strNew = "";
		if (strSrcChars.length() >= iTotalLength) {
			return strSrcChars;
		} else {
			int iNeedApndSize = iTotalLength - strSrcChars.length();
			strNew = strSrcChars;
			for (int i = 0; i < iNeedApndSize; i++) {
				strNew = strPreChar.concat(strNew);
			}
		}
		
		return strNew;
	}
	
	/**
	 * 取得易付通作为机构模式时的所需的基础资料信息。
	 * @return
	 */
	public static Map<String, String> getHarvestTransInfo() {
		Map<String, String> mapHarvestTransInfo = new HashMap<String, String>();
		mapHarvestTransInfo.put(PayOrderEntiry.APPID, CommonInfo.NOB_APP_ID);
		mapHarvestTransInfo.put(PayOrderEntiry.MCH_ID, CommonInfo.NOB_MCH_ID);
		mapHarvestTransInfo.put(PayOrderEntiry.APP_KEY, CommonInfo.NOB_KEY);
		return mapHarvestTransInfo;
	}
	
	/**
	 * 将NULL格式的字符串转换为""。
	 * @param strSrc
	 * @return
	 */
	public static String formatNullStrToSpace(String strSrc) {
		return strSrc == null ? "" : strSrc;
	}
	
	/**
	 * 将NULL或""格式的字符串转换为"0"。
	 * @param strSrc
	 * @return
	 */
	public static String formatNullStrToZero(String strSrc) {
		return strSrc == null || "".equals(strSrc) ? "0" : strSrc;
	}
	
	/**
	 * 将Double型数字依据最小、最大保留位数，进行四舍五入转换，并返回字符串类型。
	 * @param dblSrc
	 * @param iMinFract
	 * @param iMaxFract
	 * @return
	 */
	public static String formatDoubleToHalfUp(double dblSrc, int iMinFract, int iMaxFract) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setRoundingMode(RoundingMode.HALF_UP);//设置四舍五入
        nf.setMinimumFractionDigits(iMinFract);//设置最小保留几位小数
        nf.setMaximumFractionDigits(iMaxFract);//设置最大保留几位小数
        nf.setGroupingUsed(false);
        return nf.format(dblSrc);
	}
	
	/**
	 * 生成固定位数的随机数函数。
	 * 
	 * @return
	 */
	public static String getRandomString(int intLength) {
		char[] ch = new char[] { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
				'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };
		char[] chNew = new char[intLength];
		int iChLengh = ch.length;
		for (int i = 0; i < chNew.length; i++) {
			int index = (int) (iChLengh * Math.random());
			chNew[i] = ch[index];
		}

		return String.valueOf(chNew);
	}

	/**
	 * 生成商户订单号。
	 * 
	 * @param date
	 * @param intExtLength 长度应<=18，因为微信要求订单号总长度应不超过32位，由date转换的长度为14位。
	 * @return
	 */
	public static String getOutTradeNo(Date date, int intExtLength) {
		if (date == null) {
			return null;
		}

		String strPrefix = getFormatDateStr(date, "yyyyMMddHHmmss");

		char[] ch = new char[] { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' };
		char[] chNew = new char[intExtLength];
		int iChLengh = ch.length;
		for (int i = 0; i < chNew.length; i++) {
			int index = (int) (iChLengh * Math.random());
			chNew[i] = ch[index];
		}
		String strSufix = String.valueOf(chNew);

		return strPrefix.concat(strSufix);
	}
	
	/**
	 * 生成商户退款单号，此处默认为32位。
	 * @param date
	 * @param intExtLength 长度应<=18，因为微信要求订单号总长度应不超过32位，由date转换的长度为14位。
	 * @return
	 */
	public static String getOutRefundNo(Date date, int intExtLength) {
		return getOutTradeNo(date, intExtLength);
	}

	/**
	 * 根据格式字符串对日期进行格式化。
	 * 
	 * @param date
	 * @param strFormat
	 * @return
	 */
	public static String getFormatDateStr(Date date, String strFormat) {
		DateFormat sdf = new SimpleDateFormat(strFormat);
		return sdf.format(date);
	}

	/**
	 * 获取xx时长之前或之后的日期格式。
	 * @param date
	 * @param strFormat
	 * @param lngSeconds
	 * @return
	 */
	public static String getBefOrAftFormatDate(Date date, long lngMillSeconds, String strFormat) {
		Date newDate = getBefOrAftDate(date, lngMillSeconds);
		
		return getFormatDateStr(newDate, strFormat);
	}
	
	/**
	 * 将指定的日期往前或往后推固定的时间。
	 * @param date
	 * @param lngMillSeconds
	 * @return
	 */
	public static Date getBefOrAftDate(Date date, long lngMillSeconds) {
		long lngNewTime = 0;
		long lngBaseTime = date.getTime();
		if (lngMillSeconds >= 0) {	// 获取指定date参数之后的时间
			lngNewTime = lngBaseTime + Math.abs(lngMillSeconds);
		} else {	// 获取指定参数之前的时间
			lngNewTime = lngBaseTime - Math.abs(lngMillSeconds);
		}
		
		return new Date(lngNewTime);
	}
	
	/**
	 * 获取指定时间向前或向后的一定时间跨度(intTimeLen, 正负号分别表示向后或向前)、时间单位(intUnit)的时间值。
	 * @param date
	 * @param intUnit
	 * @param intTimeLen
	 * @return
	 */
	public static Date getBefOrAftDate(Date date, int intUnit, int intTimeLen) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(intUnit, intTimeLen);
		return cal.getTime();
	}
	
	/**
	 * 将字符串转换成指定格式的日期。
	 * @param strFormat
	 * @param strSourceDate
	 * @return
	 */
	public static Date getDateBaseOnChars(String strFormat, String strSourceDate) {
		if (strFormat == null || "".equals(strFormat) || strSourceDate == null || "".equals(strSourceDate)) {
			return null;
		}
		
		DateFormat sdf = new SimpleDateFormat(strFormat);
		Date date = null;
		try {
			date = sdf.parse(strSourceDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return date;
	}
	
	/**
	 * 获取当前日历参数值的日期格式。
	 * @param date
	 * @param mapArgs
	 * @return
	 */
	public static Date getDefineDateBaseOnYMDHMS(Map<Integer, Integer> mapArgs) {
		if (mapArgs == null || mapArgs.size() == 0) {
			return null;
		}
		
		Calendar cal = Calendar.getInstance();
		Integer[] intKeys = mapArgs.keySet().toArray(new Integer[0]);
		for (Integer intKey : intKeys) {
			cal.set(intKey, mapArgs.get(intKey));
		}

		return cal.getTime();
	}

	/**
	 * 获取当前终端设备的IP地址。
	 * 
	 * @return
	 * @throws UnknownHostException
	 */
	public static String getSpbill_Create_Ip() {
		InetAddress res = null;
		try {
			res = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
		return res.getHostAddress();
	}

	/**
	 * 在发送微信后台进行处理前，生成订单的签名。
	 * 
	 * @param orderEntity
	 * @param strKey
	 * @return
	 */
	public static String getEntitySign(Map<String, String> mapOrderCont) {
		String[] strContKeys = mapOrderCont.keySet().toArray(new String[0]);
		Arrays.sort(strContKeys);
		StringBuffer sb = new StringBuffer();
		for (String K : strContKeys) {
			String V = mapOrderCont.get(K);
			if (K != null && !"".equals(K) && !K.equals(CommunicateEntity.SIGN) && !K.equals(CommunicateEntity.APP_KEY) /*&& !K.equals(CommunicateEntity.AGENT_ID)*/
					&& V != null && !"".equals(V)) {
				sb.append(K.concat("=").concat(V).concat("&"));
			}
		}

		sb.append(getCorrectKey(CommunicateEntity.APP_KEY) + "=" + mapOrderCont.get(CommunicateEntity.APP_KEY));

		return new MD5Util().MD5(sb.toString(), "UTF-8").toUpperCase();
	}
	
	private static String getCorrectKey(String strFullKey) {
		if (strFullKey == null) {
			return "";
		}
		
		String strResp = "";
		if (strFullKey.contains("_")) {
			strResp = strFullKey.split("_")[1];
		}
		return strResp;
	}

	/**
	 * 获取一个新的clone的Map.
	 * 
	 * @param mapSrc
	 * @return
	 */
	public static Map<String, String> getCloneMap(Map<String, String> mapSrc) {
		if (mapSrc == null) {
			return null;
		}

		Map<String, String> newMap = new HashMap<String, String>();
		String[] strKeys = mapSrc.keySet().toArray(new String[0]);
		for (String strKey : strKeys) {
			if (strKey != null) {
				newMap.put(strKey, mapSrc.get(strKey));
			}
		}

		return newMap;
	}
	
	/**
	 * 克隆一个列表。
	 * @param listSrc
	 * @return
	 */
	public static List<String> getCloneList(List<String> listSrc) {
		if (listSrc == null) {
			return null;
		}
		
		List<String> newList = new ArrayList<String>();
		for (String strSrc : listSrc) {
			newList.add(strSrc);
		}
		
		return newList;
	}
	
	
	/**
	 * 合并两个Map的内容。
	 * @param sourceMap
	 * @param appendMap
	 * @return
	 */
	public static Map<String, String> getAppendMap(Map<String, String> sourceMap, Map<String, String> appendMap) {
		if (sourceMap == null || appendMap == null) {
			return null;
		}
		
		String[] strKeys = appendMap.keySet().toArray(new String[0]);
		for (String strKey : strKeys) {
			if (strKey != null) {
				sourceMap.put(strKey, appendMap.get(strKey));
			}
		}

		return sourceMap;
	}
	
	/**
	 * 获取两个List合并的最终列表。
	 * @param sourceList
	 * @param appendList
	 * @return
	 */
	public static List<String> getAppendList(List<String> sourceList, List<String> appendList) {
		if (sourceList == null || appendList == null) {
			return null;
		}
		
		for (String strApp : appendList) {
			sourceList.add(strApp);
		}
		
		return sourceList;
	}

	/**
	 * 格式化客户端的Socket请求字符串为Map.
	 * 
	 * @param strSocketRequest
	 * @return
	 */
	public static HashMap<String, String> formatStrToMap(String strSocketRequest) {
		if (strSocketRequest == null) {
			return null;
		}

		HashMap<String, String> hmOrderCont = new HashMap<String, String>();
		String[] strBig = strSocketRequest.split("&");
		for (String strTemp : strBig) {
			String[] strSmall = strTemp.split("=");
			hmOrderCont.put(strSmall[0], strSmall[1]);
		}

		return hmOrderCont;
	}

	
	/**
	 * 获取当前Web应用的上下文路径。
	 * @return
	 */
	public static String getAbsolutWebURL(HttpServletRequest request, boolean blnNeedPort) {
		String strAbsWebURL = request.getScheme() + "://" + request.getServerName();
		if (blnNeedPort) {
			strAbsWebURL = strAbsWebURL.concat(":" + request.getServerPort());
		}
		strAbsWebURL = strAbsWebURL.concat(request.getContextPath());
		return strAbsWebURL;
	}
	
	/**
	 * 根据不同的操作系统格式化文件存储路径。
	 * 
	 * @param strFileSeparator
	 * @return
	 */
	public static String getAbsolutWebAppPath(Class clazz, String strFileSeparator) {
//		String strOSWebAppPath = null;
		// WebApp的绝对路径
		String strWebAppPath = CommonTool.urlDecodeUTF8(clazz.getClassLoader().getResource("/").getPath().replaceAll("/WEB-INF/classes/", ""));
//		if (strWebAppPath != null && strWebAppPath.startsWith("/")) {
//			strWebAppPath = strWebAppPath.substring(1);
//		}
		System.out.println(strWebAppPath);
//		String os = System.getProperty("os.name").toLowerCase();
//		if (os.startsWith("win")) { // Windows操作系统
//			strOSWebAppPath = strWebAppPath.replaceAll("\\/", "\\" + strFileSeparator);
//		} else { // Linux或Unix操作系统
//			strOSWebAppPath = strWebAppPath; // 对路径不作替换处理
//		}
//
//		return strOSWebAppPath;
		return strWebAppPath;
	}
	
	public static String getWebAppName(ServletContext svrContext) {
		return svrContext.getContextPath();
	}

	/**
	 * 返回不含SSl证书的httpClient.
	 * 
	 * @return
	 */
	public static CloseableHttpClient getDefaultHttpClient() {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		return httpclient;
	}

	/**
	 * 返回需要SSL证书的httpClient.
	 * 
	 * @param strCertPassword
	 * @return
	 */
	public static CloseableHttpClient getCertHttpClient(String strCertPassword) {
		if (strCertPassword == null || "".equals(strCertPassword)) {
			System.out.println("证书密码为空！");
			return null;
		}

		CloseableHttpClient httpclient = null;
		/**
		 * 注意PKCS12证书 是从微信商户平台-》账户设置-》 API安全 中下载的
		 */
		KeyStore keyStore;
		try {
			keyStore = KeyStore.getInstance("PKCS12");
			String strCertFile = CommonTool.getAbsolutWebAppPath(CommonTool.class, System.getProperty("file.separator")) + "/conf/apiclient_cert.p12";
			System.out.println("strCertFile = " + strCertFile);
			FileInputStream instream = new FileInputStream(new File(strCertFile));// P12文件目录
			try {
				keyStore.load(instream, strCertPassword.toCharArray());
			} finally {
				instream.close();
			}

			// Trust own CA and all self-signed certs
			SSLContext sslcontext = SSLContexts.custom().loadKeyMaterial(keyStore, strCertPassword.toCharArray()).build();
			// Allow TLSv1 protocol only
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1" }, null, SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
			httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
		} catch (KeyStoreException | KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException
				| CertificateException | IOException e) {
			e.printStackTrace();
		}

		return httpclient;
	}
	
	/**
	 * 转换URL内的参数为UTF-8格式。
	 * 
	 * @param str
	 * @return
	 */
	public static String urlDecodeUTF8(String str) {
		if (str == null) {
			return "";
		}

		try {
			return URLDecoder.decode(str.trim(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * 转换Float或Double成为2位小数点的字符串。
	 * @return
	 */
	public static String formatNumToDoublePoints(double dblData) {
		return String.format("%.2f", dblData);
	}

	public static class MD5Util {
		private final String hexDigits[] = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };

		/**
		 * MD5加密算法。
		 * 
		 * @param sourceStr
		 * @return
		 */
		public String MD5(String sourceStr, String charsetName) {

			if (sourceStr == null || sourceStr.equals("")) {
				return null;
			}

			String resultString = null;
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				if (charsetName == null || "".equals(charsetName)) {
					resultString = byteArrayToHexString(md.digest(sourceStr.getBytes()));
				} else {
					resultString = byteArrayToHexString(md.digest(sourceStr.getBytes(charsetName)));
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				return null;
			}

			return resultString;
		}

		private String byteArrayToHexString(byte b[]) {
			StringBuffer resultSb = new StringBuffer();
			for (int i = 0; i < b.length; i++)
				resultSb.append(byteToHexString(b[i]));

			return resultSb.toString();
		}

		private String byteToHexString(byte b) {
			int n = b;
			if (n < 0)
				n += 256;
			int d1 = n / 16;
			int d2 = n % 16;
			return hexDigits[d1] + hexDigits[d2];
		}
	}
	
	/**
	 * 读取输入流内的信息。
	 * @param is
	 * @param strChrSet
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static String getInputStreamInfo(InputStream is, String strChrSet) throws UnsupportedEncodingException, IOException {
		String strReqInfo = "";
		
		if (is != null) {
			/** 获取腾讯端发送来的回调结果 **/
			BufferedReader bis = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			StringBuffer recieveData = new StringBuffer();
			String strLineInfo = null;
			while ((strLineInfo = bis.readLine()) != null) {
				recieveData.append(strLineInfo);
			}
			strReqInfo = recieveData.toString();
			
//			ByteArrayOutputStream outSteam = new ByteArrayOutputStream();  
//			int j = 0;
//			while ((j = is.read()) != -1) {
//				outSteam.write(j);
//			}
//			strReqInfo = outSteam.toString(strChrSet); //strReqInfo.concat(new String(byteBuffer, strChrSet));
		}
		
		return strReqInfo;
	}
}
