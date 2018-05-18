package com.chinaepay.wx.entity;

public class DownloadBillOrderEntity extends InquiryEntity {
	// 对账单类型为【支付单】
	public static final String BILL_ORDER_PAYMENT = "bill_payment";
	// 对账单类型为【退款单】
	public static final String BILL_ORDER_REFUND = "bill_refund";
	
	// 对账单处理状态：【处理中】
	public static final String BILL_PROCESSING = "0";
	// 对账单处理状态：【处理成功】
	public static final String BILL_PROC_SUCCESS = "1";
	// 对账单处理状态：【处理失败】
	public static final String BILL_PROC_FAIL = "2";
}
