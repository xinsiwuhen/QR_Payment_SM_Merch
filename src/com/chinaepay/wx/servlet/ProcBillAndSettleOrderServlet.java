package com.chinaepay.wx.servlet;

import com.chinaepay.wx.common.CommonTool;

public abstract class ProcBillAndSettleOrderServlet extends CommControllerServlet {

	@Override
	public boolean validSubMchIsUsable(String strSubMerchId) {
		// TODO Auto-generated method stub
		return false;
	}
	
	/**
	 * 通过输入的总金额，以及费率百分比，计算手续费金额(需四舍五入，单位为：分)
	 * @param strTotalFee	总金额，单位为：分
	 * @param strOrgRate	费率(百分制), 如：0.3则代表0.3%的费率。
	 * @return
	 */
	public String getPoundFeeBaseOnRate(String strTotalFee, String strOrgRate, int iPointNum) {
		Double dblTotlFee = Double.parseDouble(CommonTool.formatNullStrToZero(strTotalFee));
		Double dblRate = Double.parseDouble(CommonTool.formatNullStrToZero(strOrgRate));
		
		double dblRst = (dblTotlFee * dblRate) / 100;
		String strFinalRst = CommonTool.formatDoubleToHalfUp(dblRst, iPointNum, iPointNum);
		System.out.println("strFinalRst = " +  strFinalRst);
		
		return strFinalRst;
	}
}
