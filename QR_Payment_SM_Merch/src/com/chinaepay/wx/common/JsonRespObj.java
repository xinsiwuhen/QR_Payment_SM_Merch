package com.chinaepay.wx.common;

import java.io.Serializable;

public class JsonRespObj implements Serializable {
	private String respCode = null;
	private String respMsg = null;
	private Object respObj = null;
	
	public JsonRespObj() {
		this.respCode = "0";  
        this.respMsg = "";  
        this.respObj = null;
	}
	
	public JsonRespObj(String strReturnCode, String strRespMsg, Object objResp) {
		this.respCode = strReturnCode;
		this.respMsg = strRespMsg;
		this.respObj = objResp;
	}
	
	/**
	 * @return the respCode
	 */
	public String getRespCode() {
		return respCode;
	}

	/**
	 * @param respCode the respCode to set
	 */
	public void setRespCode(String respCode) {
		this.respCode = respCode;
	}

	/**
	 * @return the respMsg
	 */
	public String getRespMsg() {
		return respMsg;
	}

	/**
	 * @param respMsg the respMsg to set
	 */
	public void setRespMsg(String respMsg) {
		this.respMsg = respMsg;
	}

	/**
	 * @return the respObj
	 */
	public Object getRespObj() {
		return respObj;
	}

	/**
	 * @param respObj the respObj to set
	 */
	public void setRespObj(Object respObj) {
		this.respObj = respObj;
	}
}
