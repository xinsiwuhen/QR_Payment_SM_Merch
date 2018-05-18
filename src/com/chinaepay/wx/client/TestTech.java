package com.chinaepay.wx.client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

public class TestTech {
	public static void main(String[] args) {
		MysqlDataSource mysqlDs = new MysqlDataSource();
		mysqlDs.setURL("jdbc:mysql://112.74.90.158:3306/tmw?useUnicode=true&characterEncoding=utf-8");
		mysqlDs.setUser("root");
		mysqlDs.setPassword("lms123456");
		Connection conn = null;
		PreparedStatement prst = null;
		ResultSet rs = null;
		try {
			conn = mysqlDs.getConnection();

			String sql = "select '1494500362' org_id, 'Nob' org_type,  out_trade_no out_order_no, 'Payment' order_type,  date_format(trans_time_end, '%Y%m%d') settle_belong_date, nob_pound_fee settle_fee_amount  from tbl_pay_settle_detail_order where nob_settle_status in ('0', '3');";
			prst = conn.prepareStatement(sql);
			rs = prst.executeQuery();
			List<String[]> listTempInfo = new ArrayList<String[]>();
			while (rs.next()) {
				String strOrgId = rs.getString("org_id");
				String strOutOrderNo = rs.getString("out_order_no");
				String strOrgType = rs.getString("org_type");
				String strOrderType = rs.getString("order_type");
				String strSetBelongDate = rs.getString("settle_belong_date");
				String strSetFeeAmount = rs.getString("settle_fee_amount");

				String[] strOrderInfo = new String[] { strOrgId, strOutOrderNo, strOrgType, strOrderType,
						strSetBelongDate, strSetFeeAmount };
				System.out.println("+++--strOrderInfo = " + strOrderInfo);

				listTempInfo.add(strOrderInfo);
				System.out.println("+++--listTempInfo = " + listTempInfo);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			// MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst,
			// conn);
		}
		//
		//
		// String sql = "select * from test_settle_pay;";
		// try {
		// prst = conn.prepareStatement(sql);
		// ResultSet rs = prst.executeQuery();
		// while (rs.next()) {
		// System.out.println(rs.getString(1));
		// System.out.println(rs.getString(2));
		// System.out.println(rs.getString(3));
		// System.out.println(rs.getString(4));
		// System.out.println(rs.getString(5));
		// }
		// } catch (SQLException e) {
		// e.printStackTrace();
		// }
		//
		//
		//

		// Date date = CommonTool.getDateBaseOnChars("yyyy-MM-dd HH:mm:ss",
		// "2017-05-29 13:48:30");
		// System.out.println(date);
		// System.out.println(CommonTool.getFormatDateStr(date,
		// "yyyyMMddHHmmss"));
		//
		// double x= 1.9999998;
		// NumberFormat nf = NumberFormat.getInstance();
		// nf.setRoundingMode(RoundingMode.HALF_UP);//设置四舍五入
		// nf.setMinimumFractionDigits(0);//设置最小保留几位小数
		// nf.setMaximumFractionDigits(0);//设置最大保留几位小数
		// System.out.println(nf.format(x));

		// System.out.println(new Date().getTime());
		// System.out.println(30 * 24 * 60 * 60 * 1000);
		//
		// Date newDate = new Date(new Date().getTime() - 30 * 24 * 60 * 60 *
		// 1000L);
		// System.out.println(CommonTool.getFormatDate(newDate,
		// "yyyyMMddHHmmSS"));

		// long lngOneDay = 24 * 60 * 60 * 1000;
		// System.out.println("lngOneDay = " + -lngOneDay);
		// System.out.println("lngOneDay = " + lngOneDay);
		//
		//
		// Calendar cal = Calendar.getInstance();
		// int iYear = cal.get(Calendar.YEAR);
		// int iMonth = cal.get(Calendar.MONTH) + 1;
		// int iDay = cal.get(Calendar.DAY_OF_MONTH);
		// int iHour = 10;
		//
		// cal.set(iYear, iMonth - 1, iDay, iHour, 0, 0);
		// cal.add(Calendar.DAY_OF_MONTH, 0);
		// long lngTenOclock = cal.getTime().getTime();
		//
		//
		// Calendar cal2 = Calendar.getInstance();
		// cal2.set(Calendar.HOUR_OF_DAY, iHour);
		// cal2.set(Calendar.MINUTE, 0);
		// cal2.set(Calendar.SECOND, 0);
		// long lngTenOclock2 = cal2.getTime().getTime();
		//
		// System.out.println("10 oclock's time = " + lngTenOclock);
		// long lngNowTime = new Date().getTime();
		// System.out.println("now time = " + lngNowTime);
		//
		// System.out.println("nowTime - 10's oclock = " + (lngNowTime -
		// lngTenOclock));
		// System.out.println("nowTime - 10's oclock = " + (lngNowTime -
		// lngTenOclock2));
		//
		// // +++++++++++++++++
		// iHour = 20;
		// cal.set(iYear, iMonth - 1, iDay, iHour, 51, 00);

		Timer timer = new Timer();
		Task task = new Task(timer, true);
		// timer.schedule(task, cal.getTime());
		timer.schedule(task, 0);

		// timer = new Timer();
		// task = new Task(timer, false);
		// timer.scheduleAtFixedRate(task, cal.getTime(), 5 * 1000);

		/*
		 * DocumentBuilderFactory docBuilderFact =
		 * DocumentBuilderFactory.newInstance(); DocumentBuilder docBuilder =
		 * null; Document document = null;
		 * 
		 * try { String strWxResponseResult =
		 * "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg><result_code><![CDATA[SUCCESS]]></result_code><record_num>2</record_num><setteinfo_0><date_settlement><![CDATA[2017-05-29]]></date_settlement><date_start><![CDATA[2017-05-29]]></date_start><date_end><![CDATA[2017-05-29]]></date_end><settlement_fee>5</settlement_fee><settlementfee_type><![CDATA[USD]]></settlementfee_type><pay_fee>16</pay_fee><refund_fee>-11</refund_fee><pay_net_fee>5</pay_net_fee><poundage_fee>0</poundage_fee><unsettlement_fee>0</unsettlement_fee><fbatchno><![CDATA[201705290004]]></fbatchno></setteinfo_0><setteinfo_1><date_settlement><![CDATA[2017-05-31]]></date_settlement><date_start><![CDATA[2017-05-30]]></date_start><date_end><![CDATA[2017-05-31]]></date_end><settlement_fee>34</settlement_fee><settlementfee_type><![CDATA[USD]]></settlementfee_type><pay_fee>62</pay_fee><refund_fee>-28</refund_fee><pay_net_fee>34</pay_net_fee><poundage_fee>0</poundage_fee><unsettlement_fee>0</unsettlement_fee><fbatchno><![CDATA[201705310003]]></fbatchno></setteinfo_1></xml>";
		 * docBuilder = docBuilderFact.newDocumentBuilder(); document =
		 * docBuilder.parse(new InputSource(new
		 * StringReader(strWxResponseResult))); } catch (SAXException e) {
		 * e.printStackTrace(); } catch (ParserConfigurationException e) {
		 * e.printStackTrace(); } catch (IOException e) { e.printStackTrace(); }
		 * 
		 * appendElementNameAndValue(document);
		 * 
		 * System.out.println(mapWxRespRest);
		 */
	}

	private static Map<String, Object> mapWxRespRest = new HashMap<String, Object>();

	private static void appendElementNameAndValue(Node node) {
		if (node != null) { // 判断节点是否为空
			if (node.hasChildNodes()) { // 本元素节点下还有子节点
				NodeList nodeList = node.getChildNodes();
				String strNodeName = node.getNodeName();
				Map<String, String> mapSubInfo = new HashMap<String, String>();
				if (strNodeName.startsWith("setteinfo_")) {
					for (int i = 0; i < nodeList.getLength(); i++) {
						Node childNode = nodeList.item(i);
						mapSubInfo.put(childNode.getNodeName(), childNode.getChildNodes().item(0).getNodeValue());
					}
					mapWxRespRest.put(strNodeName, mapSubInfo);
				} else {
					for (int i = 0; i < nodeList.getLength(); i++) {
						Node childNode = nodeList.item(i);
						appendElementNameAndValue(childNode);
					}
				}
			} else { // 本元素节点下已经没有子节点
				Node nodeParent = null;
				if ((nodeParent = node.getParentNode()) != null) {
					mapWxRespRest.put(nodeParent.getNodeName(), node.getNodeValue());
					// System.out.println("[" + nodeParent.getNodeName() + " = "
					// + node.getNodeValue() + "]");
				}
			}
		}
	}

	public static class Task extends TimerTask {
		private Timer timer = null;
		boolean blnNeedCancle = false;

		public Task(Timer timer, boolean blnNeedCancle) {
			this.timer = timer;
			this.blnNeedCancle = blnNeedCancle;
		}

		@Override
		public void run() {
			Calendar cal = Calendar.getInstance();
			System.out.println("xxoo = " + cal.get(Calendar.SECOND));
			if (blnNeedCancle) {
				this.timer.cancel();
			}
		}
	}
}

class SyncObj {
	public synchronized void showA() {
		System.out.println("showA 1 ...");
		try {
			Thread.sleep(6 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("showA 2 ...");
	}

	public void showB() {
		synchronized (SyncObj.class) {
			System.out.println("showB ...");
			try {
				Thread.sleep(3 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void showC() {
		synchronized (SyncObj.class) {
			// try {
			// Thread.sleep(3 * 1000);
			// } catch (InterruptedException e) {
			// e.printStackTrace();
			// }
			System.out.println("showC ...");
		}
	}

	public void showD() {
		System.out.println("showD ...");
	}
}
