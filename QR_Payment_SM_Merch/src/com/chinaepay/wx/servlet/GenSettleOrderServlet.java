package com.chinaepay.wx.servlet;

import java.util.Date;
import java.util.Map;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.JsonRespObj;

import net.sf.json.JSONObject;

public abstract class GenSettleOrderServlet extends ProcBillAndSettleOrderServlet {
	public void init() {
		try {
			super.init();
			
			String strHour = this.getServletContext().getInitParameter("Hour_ProcMiddleSettle");
			String strMinute = this.getServletContext().getInitParameter("Minute_ProcMiddleSettle");
			String strSecond = this.getServletContext().getInitParameter("Second_ProcMiddleSettle");
			String strDelayTime = this.getServletContext().getInitParameter("DelayTime_ProcMiddleSettle");
	
			ProcSettleOrderThread psot = new ProcSettleOrderThread(strHour, strMinute, strSecond, strDelayTime);
			psot.start();
			
		} catch (ServletException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		ClosableTimer closableTimer = new ClosableTimer(true);	// 执行完任务后关闭Timer
		TimerTask task = this.getProcSettleOrderTask(closableTimer);
        closableTimer.schedule(task, 0L);
        
        JsonRespObj respObj = new JsonRespObj();
		String strProResult = "1";
		String strReturnMsg = "支付单与退款单相关的中间结算单处理任务，已经提交后台执行！";
        respObj.setRespCode(strProResult);
		respObj.setRespMsg(strReturnMsg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	public abstract ProcSettleOrderTask getProcSettleOrderTask(ClosableTimer closableTimer);
	
	public class ProcSettleOrderThread extends Thread {
		private String strHour = null;
		private String strMinute = null;
		private String strSecond = null;
		private String strDelayTime = null;
		
		public ProcSettleOrderThread(String strHour, String strMinute, String strSecond, String strDelayTime) {
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
			
			ClosableTimer closableTimer = null;
			TimerTask task = null;
			// 当前时间在11点之前, 需要等到11点时执行任务，并继续以24小时的轮询周期执行相同任务
			if (lngNowMillSec < lngDefMillSec) {
				closableTimer = new ClosableTimer(false);
		        task = getProcSettleOrderTask(closableTimer);
		        closableTimer.scheduleAtFixedRate(task, defineDate, CommonInfo.ONE_DAY_TIME);
			}
			// 当前时间在11点之后，需要马上执行一次任务，并在次日的11点开始执行一次，以后每隔24小时的轮询周期执行相同任务
			else {
				// 执行一次任务(依据抓取数据的时间)，并在任务结束后关闭
				closableTimer = new ClosableTimer(true);
				task = getProcSettleOrderTask(closableTimer);
				closableTimer.schedule(task, Long.parseLong(strDelayTime) * 60 * 1000);
				
				// 在次日10点开始执行一次任务， 以后每隔24小时的轮询周期执行相同任务
				closableTimer = new ClosableTimer(false);
				task = getProcSettleOrderTask(closableTimer);
				Date nextDay = CommonTool.getBefOrAftDate(defineDate, CommonInfo.ONE_DAY_TIME);
				closableTimer.scheduleAtFixedRate(task, nextDay, CommonInfo.ONE_DAY_TIME);
			}
		}
	}
	
	
	public abstract class ProcSettleOrderTask extends TimerTask {
		private ClosableTimer closableTimer = null;
		
		public ProcSettleOrderTask(ClosableTimer closableTimer) {
			super();
			this.closableTimer = closableTimer;
		}
		
		public void run() {
			/** 获取向下游清分的计算结果 **/
			Map<String, Map<String, String>> mapMiddleSettleInfo = this.getMiddleSettleInfo();
			
			/** 将需要向下游清分的计算结果插入数据库 **/
			boolean blnInsRst = this.insertMiddleSettleInfoToTbl(mapMiddleSettleInfo);
			
			/** 更新对账单内的[是否迁移到结算单]状态为“已迁移” **/
			if (blnInsRst) {
				this.updateReconResultTransfStatus(mapMiddleSettleInfo);
			}
			
			/** 判断是否需要关闭任务时钟 **/
			if (closableTimer.isNeedClose()) {
				closableTimer.cancel();
			}
		}
		
		public abstract Map<String, Map<String, String>> getMiddleSettleInfo();
		
		public abstract boolean insertMiddleSettleInfoToTbl(Map<String, Map<String, String>> mapMiddleSettleInfo);
		
		public abstract void updateReconResultTransfStatus(Map<String, Map<String, String>> mapMiddleSettleInfo);
	}
}
