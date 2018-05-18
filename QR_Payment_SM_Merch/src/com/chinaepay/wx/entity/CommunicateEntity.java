/**
 * @author xinwuhen
 */
package com.chinaepay.wx.entity;

/**
 * @author xinwuhen
 *
 */
public class CommunicateEntity {
//	/** 以下常量用于商户与易付通之间的接口定义 **/
//	// 系统交互结果
//	public static final String SYSTEM_COMM_RESULT_KEY = "SYSTEM_COMM_RESULT";	// 数值为：SUCCESS 或  FAIL
//	// 业务处理是否成功的标识，值为: SUCCESS 或 [微信提供的错误码]
//	public static final String BUSINESS_PROC_RESULT_KEY = "BUSINESS_PROC_RESULT";
//	// 标识每次交易或查询函数调用结果的返回报文的
//	public static final String BUSINESS_RESPONSE_RESULT = "BUSINESS_RESPONSE_RESULT";
	
	/** 以下常量标识请求报文中的参数名 **/
	// 代理商ID
	public static final String AGENT_ID = "agent_id";
	// 公众账号ID
	public static final String APPID = "appid";
	// 商户号
	public static final String MCH_ID = "mch_id";
	// 子商户号
	public static final String SUB_MCH_ID = "sub_mch_id";
	// 微信订单号
	public static final String TRANSACTION_ID = "transaction_id";
	// 商户订单号
	public static final String OUT_TRADE_NO = "out_trade_no";
	// 预支付单ID
	public static final String PREPAY_ID = "prepay_id";
	// 用户支付方式（1：扫码支付； 2： 公众号支付；3：刷卡支付；4：APP支付；5: H5支付；6：小程序支付。）
	public static final String USER_PAY_TYPE = "user_pay_type";
	// 支付完成后的回调地址
	public static final String NOTIFY_URL = "notify_url";
	// 商户退款单号
	public static final String OUT_REFUND_NO = "out_refund_no";
	// 随机字符串
	public static final String NONCE_STR = "nonce_str";
	// 签名
	public static final String SIGN = "sign";
	// 商品描述	
	public static final String BODY = "body";
	// 商户的KEY
	public static final String APP_KEY = "app_key";
	// 标价金额
	public static final String TOTAL_FEE = "total_fee";
	// 退款金额
	public static final String REFUND_FEE = "refund_fee";
	// 退款币种
	public static final String REFUND_FEE_TYPE = "refund_fee_type";
	// 退款原因
	public static final String REFUND_DESC = "refund_desc";
	// 退款笔数
	public static final String REFUND_COUNT = "refund_count";
	// 标价币种
	public static final String FEE_TYPE = "fee_type";
	// 终端IP
	public static final String SPBILL_CREATE_IP = "spbill_create_ip";
	// 授权码
	public static final String AUTH_CODE = "auth_code";
	// 用户标识
	public static final String OPEN_ID = "openid";
	// 交易类型
	public static final String TRADE_TYPE = "trade_type";
	// 付款银行
	public static final String BANK_TYPE = "bank_type";
	// 现金支付币种	
	public static final String CASH_FEE_TYPE = "cash_fee_type";
	// 现金退款金额
	public static final String CASH_REFUND_FEE = "cash_refund_fee";
	// 现金退款币种
	public static final String CASH_REFUND_FEE_TYPE = "cash_refund_fee_type";
	// 现金支付金额	
	public static final String CASH_FEE = "cash_fee";
	// 汇率	
	public static final String RATE = "rate";
	// 退款状态
	public static final String REFUND_STATUS = "refund_status";
	// 退款渠道
	public static final String REFUND_CHANNEL = "refund_channel";
	// 微信退款单号
	public static final String REFUND_ID = "refund_id";
	// 退款入账账户
	public static final String REFUND_RECV_ACCOUT = "refund_recv_accout";
	
	/** 以下常量标识应答报文中的参数名 **/
	// 返回状态码：此字段是通信标识，非交易标识，交易是否成功需要查看result_code来判断
	public static final String RETURN_CODE = "return_code";
	// 业务结果	
	public static final String RESULT_CODE = "result_code";
	// 通信错误时返回的描述信息
	public static final String RETURN_MSG = "return_msg";
	// 业务结果错误时返回的描述信息
	public static final String ERR_CODE_DES = "err_code_des";
	// 通信或业务交易结果：成功 如：支付成功
	public static final String SUCCESS = "SUCCESS";
	// 错误代码
	public static final String ERR_CODE = "err_code";
	// 通信或业务交易结果：失败
	public static final String FAIL = "FAIL";
	// 交易发起时间
	public static final String TRANS_TIME = "trans_time";
	// 完成时间
	public static final String TIME_END = "time_end";
	// 订单失效时间
	public static final String TIME_EXPIRE = "time_expire";
	// 退款成功时间
	public static final String REFUND_SUCCESS_TIME = "refund_success_time";
	// 退款资金来源
	public static final String REFUND_ACCOUNT = "refund_account";
	// 交易状态	
	public static final String  TRADE_STATE = "trade_state";
	// 交易状态描述
	public static final String TRADE_STATE_DESC = "trade_state_desc";
	// 接口返回错误
	public static final String SYSTEMERROR = "SYSTEMERROR";
	// 用户支付中，需要输入密码
	public static final String USERPAYING = "USERPAYING";
	// 银行系统异常
	public static final String BANKERROR = "BANKERROR";
	// 参数错误
	public static final String PARAM_ERROR = "PARAM_ERROR";
	// 转入退款
	public static final String REFUND = "REFUND";
	// 未支付
	public static final String NOTPAY = "NOTPAY";
	// 已关闭
	public static final String CLOSED = "CLOSED";
	// 已撤销(刷卡支付)
	public static final String REVOKED = "REVOKED";
	// 处理中
	public static final String PROCESSING = "PROCESSING";
	// 支付失败(其他原因，如银行返回失败)
	public static final String PAYERROR = "PAYERROR";
	// 此交易订单号不存在
	public static final String ORDERNOTEXIST = "ORDERNOTEXIST";
	// 所有订单或内容
	public static final String ALL = "ALL";
	// 充值后退款
	public static final String RECHARGE_REFUND = "RECHARGE_REFUND";
	// 是否重调	
	public static final String RECALL = "recall";
	// 对账单日期
	public static final String BILL_DATE = "bill_date";
	// 账单类型
	public static final String BILL_TYPE = "bill_type";
	
	/** 用户支付类型(1：扫码支付； 2： 公众号支付；3：刷卡支付；4：APP支付；5: H5支付；6：小程序支付。) **/
	// 1.扫码支付
	public static final String PAY_TYPE_SCAN_QR = "1";
	// 2.公众号支付
	public static final String PAY_TYPE_SNS = "2";
	// 3.刷卡支付
	public static final String PAY_TYPE_FLASH_CARD = "3";
	// 4.APP支付
	public static final String PAY_TYPE_APP = "4";
	// 5.H5支付
	public static final String PAY_TYPE_H5 = "5";
	// 6.小程序支付
	public static final String PAY_TYPE_MICRO_APP = "6";
	
	/** 商户/账户相关状态值 **/
	/* 审核状态 */
	// 1 待审核
	public static final String AUDIT_STATUS_WAIT = "1";
	// 2 美唯审核
	public static final String AUDIT_STATUS_HARVEST_VALID = "2";
	// 3 银行审核
	public static final String AUDIT_STATUS_BANK_VALID = "3";
	// 4 审核通过
	public static final String AUDIT_STATUS_OK = "4";
	// -1 审核不通过
	public static final String AUDIT_STATUS_FAIL = "-1";
	/* 商户状态 1 启用 2禁用 */
	// 1 启用
	public static final String MERCHANT_STATUS_OK = "1";
	// 2 禁用
	public static final String MERCHANT_STATUS_NG = "2";
	/* 账户状态  1正常 2 冻结 */
	// 1 正常
	public static final String ACCOUNT_STATUS_OK = "1";
	// 2 冻结
	public static final String ACCOUNT_STATUS_NG = "1";
	/* 是否删除账户 1:已删除  0:未删除 */
	// 1 已删除
	public static final String ACCOUNT_DELETED_OK = "1";
	// 0 未删除
	public static final String ACCOUNT_DELETED_NG = "0";
}
