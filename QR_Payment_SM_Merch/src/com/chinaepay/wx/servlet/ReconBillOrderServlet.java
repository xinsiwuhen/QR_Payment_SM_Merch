package com.chinaepay.wx.servlet;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.JsonRespObj;

import net.sf.json.JSONObject;

public abstract class ReconBillOrderServlet extends ProcBillAndSettleOrderServlet {
	public void init() {
		try {
			super.init();
			
			// 获取比对对账单任务启动的指定时间
			String strHour = this.getServletContext().getInitParameter("Hour_ReconBill");
			String strMinute = this.getServletContext().getInitParameter("Minute_ReconBill");
			String strSecond = this.getServletContext().getInitParameter("Second_ReconBill");
			String strDelayTime = this.getServletContext().getInitParameter("DelayTime_ReconBill");
	
			// 启动比对对账单的任务线程
			ReconBillOrderThread dbpot = new ReconBillOrderThread(strHour, strMinute, strSecond, strDelayTime);
			dbpot.start();
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {

		// 【注意】：时间格式精确到“天”，如：20180302
		String startDayForRecBillOrder = request.getParameter("startDayForReconBillOrder"); 
		String endDayForRecBillOrder = request.getParameter("endDayForReconBillOrder");
		
		ClosableTimer closableTimer = new ClosableTimer(true);	// 执行完任务后关闭Timer
		TimerTask task = this.getReconBillOrderTask(closableTimer, startDayForRecBillOrder, endDayForRecBillOrder);
        closableTimer.schedule(task, 0L);
        
        JsonRespObj respObj = new JsonRespObj();
		String strGenResult = "1";
		String strReturnMsg = "生成对账单(含：支付单与退款单)任务已经提交后台执行！";
        respObj.setRespCode(strGenResult);
		respObj.setRespMsg(strReturnMsg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	public abstract ReconBillOrderTask getReconBillOrderTask(ClosableTimer closableTimer, String strReconStartDay, String strReconEndDay);
	
	/**
	 * 对账单比对任务的启动线程。
	 * @author xinwuhen
	 */
	public class ReconBillOrderThread extends Thread {
		private String strHour = null;
		private String strMinute = null;
		private String strSecond = null;
		private String strDelayTime = null;
		
		public ReconBillOrderThread(String strHour, String strMinute, String strSecond, String strDelayTime) {
			this.strHour = strHour;
			this.strMinute = strMinute;
			this.strSecond = strSecond;
			this.strDelayTime = strDelayTime;
		}
		
		public void run() {
			// 当前时间
			Date nowDate =  new Date();
			long lngNowMillSec = nowDate.getTime();
			
			// 获取指定日历参数的时间
			Date defineDate = getFixDateBasedOnArgs(strHour, strMinute, strSecond);
			long lngDefMillSec = defineDate.getTime();
			
			String strReconStartDay = CommonTool.getBefOrAftFormatDate(nowDate, -CommonInfo.ONE_DAY_TIME, "yyyyMMdd");
			String strReconEndDay = strReconStartDay;	// 默认执行昨天全天的对账单核对
			ClosableTimer closableTimer = null;
			TimerTask task = null;
			
			System.out.println("+++lngNowMillSec = " + lngNowMillSec);
			System.out.println("+++lngDefMillSec = " + lngDefMillSec);
			
			// 当前时间在11点之前, 需要等到11点时执行任务，并继续以24小时的轮询周期执行相同任务
			if (lngNowMillSec < lngDefMillSec) {
				closableTimer = new ClosableTimer(false);
		        task = getReconBillOrderTask(closableTimer, strReconStartDay, strReconEndDay);
		        closableTimer.scheduleAtFixedRate(task, defineDate, CommonInfo.ONE_DAY_TIME);
			}
			// 当前时间在11点之后，需要马上执行一次任务，并在次日的11点开始执行一次，以后每隔24小时的轮询周期执行相同任务
			else {
				// 执行一次任务(依据抓取数据的时间)，并在任务结束后关闭
				closableTimer = new ClosableTimer(true);
				task = getReconBillOrderTask(closableTimer, strReconStartDay, strReconEndDay);
				closableTimer.schedule(task, Long.parseLong(strDelayTime) * 60 * 1000);	// 将分钟转换为毫秒
				
				// 在次日10点开始执行一次任务， 以后每隔24小时的轮询周期执行相同任务
				closableTimer = new ClosableTimer(false);
				task = getReconBillOrderTask(closableTimer, strReconStartDay, strReconEndDay);
				Date nextDay = CommonTool.getBefOrAftDate(defineDate, CommonInfo.ONE_DAY_TIME);
				closableTimer.scheduleAtFixedRate(task, nextDay, CommonInfo.ONE_DAY_TIME);
			}
		}
	}
	
	public abstract class ReconBillOrderTask extends TimerTask {
		public ClosableTimer closableTimer = null;
		public String strReconStartDay = null;
		public String strReconEndDay = null;
		
		public ReconBillOrderTask(ClosableTimer closableTimer, String strReconStartDay, String strReconEndDay) {
			super();
			this.closableTimer = closableTimer;
			this.strReconStartDay = strReconStartDay;
			this.strReconEndDay = strReconEndDay;
		}
		
		public void run() {
			// 插入NOB(Harvest)侧存在，而Tencent侧不存在的记录
			Map<String, Map<String, String>> mapOuterInfo = this.getNobMoreThanWxOrderRecord(strReconStartDay, strReconEndDay);
			System.out.println("NobMoreThanWx's mapOuterInfo = " + mapOuterInfo);
			this.insertFullRecInfoToTbl(mapOuterInfo);
			
			// 插入Tencent侧存在，而NOB(Harvest)侧不存在的记录
			mapOuterInfo = this.getWxMoreThanNobOrderRecord(strReconStartDay, strReconEndDay);
			System.out.println("WxMoreThanNob's mapOuterInfo = " + mapOuterInfo);
			this.insertFullRecInfoToTbl(mapOuterInfo);
			
			// 插入NOB(Harvest)侧与Tencent侧都存在的记录
			mapOuterInfo = this.getWxEqualNobOrderRecord(strReconStartDay, strReconEndDay);
			System.out.println("WxEqualNob's mapOuterInfo = " + mapOuterInfo);
			this.insertFullRecInfoToTbl(mapOuterInfo);
			
			// 判断是否需要关闭任务时钟
			System.out.println("closableTimer.isNeedClose() = " + closableTimer.isNeedClose());
			if (closableTimer.isNeedClose()) {
				closableTimer.cancel();
			}
		}
		
		/**
		 * 插入NOB(Harvest)侧存在，而Tencent侧不存在的记录。
		 * @param strReconStartDay
		 * @param strReconEndDay
		 */
		public abstract Map<String, Map<String, String>> getNobMoreThanWxOrderRecord(String strReconStartDay, String strReconEndDay);
		
		/**
		 * 插入Tencent侧存在，而NOB(Harvest)侧不存在的记录。
		 * @param strReconStartDay
		 * @param strReconEndDay
		 */
		public abstract Map<String, Map<String, String>> getWxMoreThanNobOrderRecord(String strReconStartDay, String strReconEndDay);
		
		/**
		 * 插入NOB(Harvest)侧与Tencent侧都存在的记录。
		 * @param strReconStartDay
		 * @param strReconEndDay
		 */
		public abstract Map<String, Map<String, String>> getWxEqualNobOrderRecord(String strReconStartDay, String strReconEndDay);
		
		/**
		 * 将存储在容器中的完整信息插入对账结果数据库。
		 * @param mapOuterInfo
		 */
		public abstract void insertFullRecInfoToTbl(Map<String, Map<String, String>> mapOuterInfo);
		
		/**
		 * 校验当前支付单所属子商户是否配置相应的模板。
		 * @param strOrderNo
		 * @return
		 */
		public abstract boolean validRefTemplet(String strOrderNo);
		
		/**
		 * 为了不再更新对账结果表内对账成功的记录，需要将传入该方法的数据集剔除这些成功的数据。
		 * @param mapOuterInfo
		 * @param lstOutTradeNoRecOk
		 * @return
		 */
		public Map<String, Map<String, String>> getNewOuterInfo(Map<String, Map<String, String>> mapOuterInfo, List<String> lstOutTradeNoRecOk) {
			Map<String, Map<String, String>> mapNewOuterInfo = new HashMap<String, Map<String, String>>();
			
			String[] strOutTradeNos = mapOuterInfo.keySet().toArray(new String[0]);
			for (String strOutTradeNo : strOutTradeNos) {
				if (!lstOutTradeNoRecOk.contains(strOutTradeNo)) {	// 从原始对比结果中取出的记录，不包含在上次对账成功的结果集中
					mapNewOuterInfo.put(strOutTradeNo, mapOuterInfo.get(strOutTradeNo));
				}
			}
			
			return mapNewOuterInfo;
		}
	}
}
