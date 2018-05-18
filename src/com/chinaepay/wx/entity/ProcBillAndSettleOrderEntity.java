package com.chinaepay.wx.entity;

public class ProcBillAndSettleOrderEntity extends CommunicateEntity {
	// 模板ID
	public static final String POUND_FEE_TEMP_ID = "pound_fee_temp_id";
	// 随机立减金额
	public static final String DISCOUNT_FEE = "discount_fee";
	// 退款随机立减金额
	public static final String DISCOUNT_REFUND_FEE = "discount_refund_fee";
	
	// 腾讯侧手续费
	public static final String SERVICE_POUND_FEE = "service_pound_fee";
	// 腾讯侧手续费率
	public static final String POUND_RATE = "pound_rate";
	
	// NOB侧是否存在记录
	public static final String REC_NOB_EXIST = "rec_nob_exist";
	// NOB侧实际金额
	public static final String REC_NOB_ACT_FEE = "rec_nob_act_fee";
	// Tencent侧是否存在记录
	public static final String REC_WX_EXIST = "rec_wx_exist";
	// Tencent侧实际金额
	public static final String REC_WX_ACT_FEE = "rec_wx_act_fee";
	// NOB侧比Tencent侧所少的金额(单位：分)
	public static final String REC_NOB_LESS_WX_FEE = "rec_nob_less_wx_fee";
	// 对账单核对结果
	public static final String REC_RESULT = "rec_result";
	// 是否被转入结算单处理进程
	public static final String IS_TRANSF_SETTLE = "is_transf_settle";
	// 结算币种
	public static final String SETTLEMENT_FEE_TYPE = "settlementfee_type";
	// 最终结算单号
	public static final String SETTLEMENT_ORDER_ID = "settle_order_id";
	// NOB手续费
	public static final String NOB_POUND_FEE = "nob_pound_fee";
	// NOB结算状态
	public static final String NOB_SETTLE_STATUS = "nob_settle_status";
	// NOB结算单ID
	public static final String NOB_SETTLE_ID = "nob_settle_id";
//	// NOB批次号
//	public static final String NOB_SETTLE_BACH_NO = "nob_settle_bach_no";
	// Harvest手续费
	public static final String HAR_POUND_FEE = "har_pound_fee";
	// Harvest结算状态
	public static final String HAR_SETTLE_STATUS = "har_settle_status";
	// Harvest结算单ID
	public static final String HAR_SETTLE_ID = "har_settle_id";
//	// Harvest批次号
//	public static final String HAR_SETTLE_BACH_NO = "har_settle_bach_no";
	// Agent手续费
	public static final String AGEN_POUND_FEE = "agen_pound_fee";
	// Agent结算状态
	public static final String AGEN_SETTLE_STATUS = "agen_settle_status";
	// Agent结算单ID
	public static final String AGEN_SETTLE_ID = "agen_settle_id";
//	// Agent批次号
//	public static final String AGEN_SETTLE_BACH_NO = "agen_settle_bach_no";
	// SUBMCH实际需结算金额
	public static final String SUBMCH_SETTLE_FEE = "submch_settle_fee";
	// SUBMCH结算状态
	public static final String SUBMCH_SETTLE_STATUS = "submch_settle_status";
	// SUBMCH结算单ID
	public static final String SUBMCH_SETTLE_ID = "submch_settle_id";
//	// SUBMCH批次号
//	public static final String SUBMCH_SETTLE_BACH_NO = "submch_settle_bach_no";
	
	// 组织类型，sub_mch: 子商户  agent：代理   harvest：易付通美国  nob：NOB银行
	// 子商户
	public static final String ORG_TYPE_SUB_MCH = "Submch";
	// 代理
	public static final String ORG_TYPE_AGENT = "Agent";
	// 易付通美国
	public static final String ORG_TYPE_HARVEST = "Harvest";
	// NOB银行
	public static final String ORG_TYPE_NOB = "Nob";
	
	// 订单类型
	// 支付单
	public static final String ORDER_TYPE_PAYMENT = "Payment";  
	// 退款单
	public static final String ORDER_TYPE_REFUND = "Refund";
		
	/** 最终结算单信息 **/
	// 机构ID
	public static final String ORG_ID = "org_id";
	// 结算单批次号
	public static final String SETTLE_BATCHNO = "settle_batchno";
	// 机构类型, sub_mch: 子商户  agent：代理   harvest：易付通美国  nob：NOB银行
	public static final String ORG_TYPE = "org_type";
	// 结算单对应的交易日期
	public static final String SETTLE_BELONG_DATE = "settle_belong_date";
	// 结算开始时间
	public static final String SETTLE_START_TIME = "settle_start_time";
	// 结算结束时间
	public static final String SETTLE_END_TIME = "settle_end_time";
	// 结算金额 
	public static final String SETTLE_FEE_AMOUNT = "settle_fee_amount";
	// 结算货币类型
	public static final String SETTLE_FEE_TYPE = "settle_fee_type";
	// 结算状态
	public static final String SETTLE_STATUS = "settle_status";
	// 商户银行名称
	public static final String ORG_BANK_NAME = "org_bank_name";
	// 商户银行账户
	public static final String ORG_BANK_ACCOUNT = "org_bank_account";
	
	/** 结算状态数值(0：待结算  1：结算中  2：已结算  3：结算失败) **/
	// 待结算
	public static final String SETTLE_WAITING = "0";
	// 结算中
	public static final String SETTLE_PROCESSING = "1";
	// 已结算
	public static final String SETTLE_SUCESS = "2";
	// 结算失败
	public static final String SETTLE_FAIL = "3";
}
