package com.chinaepay.wx.servlet;

import com.chinaepay.wx.entity.DownloadBillRefundOrderEntity;

/**
 * 下载退款单对应的对账单信息。
 * @author xinwuhen
 */
public class DownloadBillRefundOrderServlet extends DownloadBillOrderServlet {

	@Override
	public DownloadBillOrderTask getNewDownloadBillOrderTask(ClosableTimer closableTimer) {
		return new DownloadBillRefundOrderTask(closableTimer);
	}

	@Override
	public DownloadBillOrderTask getNewDownloadBillOrderTask(ClosableTimer closableTimer, String startTimeForBillOrderSucc, String endTimeForBillOrderSucc) {
		return new DownloadBillRefundOrderTask(closableTimer, startTimeForBillOrderSucc, endTimeForBillOrderSucc);
	}
	
	/**
	 * 下载对账单（支付单）任务。
	 * @author xinwuhen
	 */
	public class DownloadBillRefundOrderTask extends DownloadBillOrderTask {
		public DownloadBillRefundOrderTask(ClosableTimer closableTimer) {
			super(closableTimer);
		}
		
		public DownloadBillRefundOrderTask(ClosableTimer closableTimer, String startTimeForBillOrderSucc, String endTimeForBillOrderSucc) {
			super(closableTimer, startTimeForBillOrderSucc, endTimeForBillOrderSucc);
		}

		@Override
		public void run() {
			downloadAndUpdateBillInfo(DownloadBillRefundOrderEntity.REFUND);
		}

		/**
		 * 生成不同的对账单表所需要的SQL语句（批量更新）。
		 * @return
		 */
		public String getUpdateBillOrderBatchSql() {
			String strSql = "replace into tbl_wx_refund_bill_info(trans_time, appid, mch_id, sub_mch_id, device_info, transaction_id, out_trade_no, "
							+ " openid, trade_type, trade_state, bank_type, fee_type, total_fee, discount_fee, refund_trans_time, refund_success_time, "
							+ " refund_id, out_refund_no, refund_fee, discount_refund_fee, refund_type, refund_status, goods_name, mch_data_info, "
							+ "	service_pound_fee, pound_rate, cash_fee_type, cash_fee, settl_currency_type, settl_currency_amount, exchange_rate, "
							+ " refund_exchange_rate, payer_refund_amount, payer_refund_currency_type, refund_currency_type, refund_settl_currency_type, "
							+ " refund_settl_amount) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
							+ " ?, ?, ?, ?, ?, ?);";
			return strSql;
		}
	}
}
