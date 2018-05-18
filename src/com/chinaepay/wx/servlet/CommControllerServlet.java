package com.chinaepay.wx.servlet;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.entity.CommunicateEntity;

public abstract class CommControllerServlet extends ExtendsHttpServlet {
	
	/**
	 * 校验商户是否有效。
	 * @param strSubMerchId
	 * @return
	 */
	public abstract boolean validSubMchIsUsable(String strSubMerchId);
	
	/**
	 * 在发送微信后台进行处理前，将订单实体格式化请求文件为XML格式。
	 * @param orderEntity
	 * @return
	 */
	public String formatReqInfoToXML(Map<String, String> mapRequestInfo) {
		String strXML = "";
		
		String[] strKeys = mapRequestInfo.keySet().toArray(new String[0]);
		if (strKeys.length > 0) {
			strXML = strXML.concat("<xml>");
			
			StringBuffer sb = new StringBuffer();
			for (String strKey : strKeys) {
				if (strKey != null && !"".equals(strKey) && !strKey.equals(CommunicateEntity.APP_KEY) /*&& !strKey.equals(CommunicateEntity.AGENT_ID)*/) {
					String strValue = mapRequestInfo.get(strKey);
					if (strValue != null /*&& !"".equals(strValue)*/) {
						sb.setLength(0);
						sb.append("<").append(strKey).append(">").append(strValue).append("</").append(strKey).append(">");
						strXML = strXML.concat(sb.toString());
					}
				}
			}
			
			strXML = strXML.concat("</xml>");
		}
		
		return strXML;
	}
	
	/**
	 * 发送HttpPost请求，并获取应答报文。
	 * @param strURI
	 * @param lstNameValuePair
	 * @return
	 */
	public CloseableHttpResponse sendAndGetHttpPostRst(String strURI, List<BasicNameValuePair> lstNameValuePair) {
		CloseableHttpResponse response = null;
		// 创建httpclient对象
		CloseableHttpClient client = CommonTool.getDefaultHttpClient();
		// 创建post方式请求对象
		HttpPost httpPost = new HttpPost(strURI); // 设置响应头信息
		// 设置参数到请求对象中
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(lstNameValuePair, "UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			return response;
		}

		// 设置header信息
		// 指定报文头【Content-type】、【User-Agent】
		httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
		httpPost.setHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
		
		// 执行请求操作，并拿到结果（同步阻塞）
		try {
			response = client.execute(httpPost);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return response;
	}
	
	/**
	 * 通过指定的年、月、日，获取相对应的日期。
	 * @param strHour
	 * @param strMinute
	 * @param strSecond
	 * @return
	 */
	public Date getFixDateBasedOnArgs(String strHour, String strMinute, String strSecond) {
		Map<Integer, Integer> mapCalArgs = new HashMap<Integer, Integer>();
		mapCalArgs.put(Calendar.HOUR_OF_DAY, Integer.valueOf(CommonTool.formatNullStrToZero(strHour)));
		mapCalArgs.put(Calendar.MINUTE, Integer.valueOf(CommonTool.formatNullStrToZero(strMinute)));
		mapCalArgs.put(Calendar.SECOND, Integer.valueOf(CommonTool.formatNullStrToZero(strSecond)));
		
		return CommonTool.getDefineDateBaseOnYMDHMS(mapCalArgs);
	}
	
	
	/**
	 * 创建可以在执行任务后关停的时钟类。
	 * @author xinwuhen
	 */
	public class ClosableTimer extends Timer {
		private boolean blnNeedCloseTimer = false;
		
		public ClosableTimer(boolean blnNeedCloseTimer) {
			super();
			this.blnNeedCloseTimer = blnNeedCloseTimer;
		}
		
		public boolean isNeedClose() {
			return blnNeedCloseTimer;
		}
	}
	
	
	/**
	 * 此为一个内部类，用于解析微信端后台返回的XML应答内容。
	 * @author xinwuhen
	 *
	 */
	public class ParsingWXResponseXML {
		private Map<String, String> mapWXRespResult = new HashMap<String, String>();

		/**
		 * 解析XML并保存在Map中。
		 * @param strWxResponseResult
		 * @return
		 * @throws ParserConfigurationException 
		 * @throws IOException 
		 * @throws SAXException 
		 */
		public Map<String, String> formatWechatXMLRespToMap(String strWxResponseResult) {
			if (strWxResponseResult == null || "".equals(strWxResponseResult)) {
				return null;
			}
			
			if (strWxResponseResult.toLowerCase().startsWith("<xml>")) {
				DocumentBuilderFactory docBuilderFact = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = null;
				Document document = null;
				
				try {
					docBuilder = docBuilderFact.newDocumentBuilder();
					document = docBuilder.parse(new InputSource(new StringReader(strWxResponseResult)));
				} catch (SAXException e) {
					e.printStackTrace();
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				// 解析XML格式的字符串，并将字符串的以【名-值】对的形式添加到MAP中。
				if (document != null) {
					appendElementNameAndValue(document);
				}
			}
			
			return mapWXRespResult;
		}
		
		/**
		 * 取得元素节点的节点名跟节点值。
		 * @param node
		 * @return
		 */
		private void appendElementNameAndValue(Node node) {
			if (node != null) {  // 判断节点是否为空
				if (node.hasChildNodes()) {	// 本元素节点下还有子节点
					NodeList nodeList = node.getChildNodes();
					for (int i = 0; i < nodeList.getLength(); i++) {
						Node childNode = nodeList.item(i);
						appendElementNameAndValue(childNode);
					}
				} else {	// 本元素节点下已经没有子节点
					Node nodeParent = null;
					if ((nodeParent = node.getParentNode()) != null) {
						mapWXRespResult.put(nodeParent.getNodeName(), node.getNodeValue());
					}
				}
			}
		}
	}
}
