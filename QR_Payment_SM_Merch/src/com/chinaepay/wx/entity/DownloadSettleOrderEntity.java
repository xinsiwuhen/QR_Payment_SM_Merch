package com.chinaepay.wx.entity;

public class DownloadSettleOrderEntity extends InquiryEntity {
	// 结算单处理状态：【处理中】
	public static final String SETTLE_PROCESSING = "0";
	// 结算单处理状态：【处理成功】
	public static final String SETTLE_PROC_SUCCESS = "1";
	// 结算单处理状态：【处理失败】
	public static final String SETTLE_PROC_FAIL = "2";
	
	// 结算状态
	public static final String USETAG = "usetag";
	// 已结算查询
	public static final String HAS_SETTLED_INQUIRY ="1";
	// 未结算查询
	public static final String No_SETTLED_INQUIRY ="2";
	
	// 编移量
	public static final String OFFSET = "offset";
	// 最大记录数
	public static final String LIMIT = "limit";
	
	// 结算单所属开始日期
	public static final String DATE_START = "date_start";
	// 结算单结束日期
	public static final String DATE_END = "date_end"; 
	
	// 查询一定时间段内的结算单时，返回的总条数
	public static final String RECORD_NUM = "record_num";
	
	// 结算单批次号
	public static final String FBATCHNO = "fbatchno";
	// 结算日期（=“交易结束日期”）
	public static final String DATE_SETTLEMENT = "date_settlement";
	// 划账金额
	public static final String SETTLEMENT_FEE = "settlement_fee";
	// 未划账金额
	public static final String UNSETTLEMENT_FEE = "unsettlement_fee";
	// 结算币种
	public static final String SETTLEMENTFEE_TYPE = "settlementfee_type";
	// 支付金额
	public static final String PAY_FEE = "pay_fee";
	// 支付净额
	public static final String PAY_NET_FEE = "pay_net_fee";
	// 手续费金额
	public static final String POUNDAGE_FEE = "poundage_fee";
}
