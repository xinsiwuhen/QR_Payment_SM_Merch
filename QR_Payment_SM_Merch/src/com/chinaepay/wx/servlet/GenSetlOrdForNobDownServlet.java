package com.chinaepay.wx.servlet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.common.JsonRespObj;
import com.chinaepay.wx.entity.GenSetlOrdForNobDownEntity;
import com.chinaepay.wx.entity.MergPayAndRefundSettleEntity;

import net.sf.json.JSONObject;

/**
 * 创建供NOB下载的结算单文件。
 * @author xinwuhen
 */
public class GenSetlOrdForNobDownServlet extends ProcSettleOrderServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		JsonRespObj respObj = new JsonRespObj();
		String strProcResult = "1";
		String strReturnMsg = "";
		String strSettleFileURL = "";
		
		String strSettlStartDay = request.getParameter("settlOrderStartDay");
		String strSettlEndDay = request.getParameter("settlOrderEndDay");
		if (strSettlStartDay == null || "".equals(strSettlStartDay) || strSettlEndDay == null || "".equals(strSettlEndDay)) {
			strProcResult = "0";
			strReturnMsg = "需导出的结算单开始日期或结束日期为空.";
			strSettleFileURL = "";
		} else {
			// 获取结算单状态为【结算中】的结算单列表; 
			// 对于状态为【结算失败】的结算单，需要由后台生成结算单的进程在次日统一处理，将【结算失败】状态更新为【结算中】时才允许NOB进行结算
			List<Map<String, String>> listUnSettleInfo = this.getUnSettleOrderInfo(strSettlStartDay, strSettlEndDay);
			
			if (listUnSettleInfo == null || listUnSettleInfo.size() == 0) {
				strProcResult = "0";
				strReturnMsg = "没有符合导出条件的结算单.";
				strSettleFileURL = "";
			} else {
				// 生成结算单文件，并返回结算单下载的URL地址
				strSettleFileURL = this.genSetlFileAndGetDownURL(listUnSettleInfo);
				
				if (strSettleFileURL != null && !"".equals(strSettleFileURL)) {	// 返回地址不为空，代表结算单已经生成
					strProcResult = "1";
					strReturnMsg = "生成[" + strSettlStartDay + "]至[" + strSettlEndDay + "]日期间的结算单成功.";
				}
			}
		}
		
		// 根据不同的业务场景，返回不同的Json对象，用于客户端判断
		respObj.setRespCode(strProcResult);
		respObj.setRespMsg(strReturnMsg);
		respObj.setRespObj(strSettleFileURL);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
		
//		String strValidTotalFeeURL = CommonTool.getAbsolutWebURL(request, true) + "/../" + strSettleFileURL;
//		try {
//			response.sendRedirect(strValidTotalFeeURL);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	// 获取未结算的结算单列表
	private List<Map<String, String>> getUnSettleOrderInfo(String strSettlStartDay, String strSettlEndDay) {
		List<Map<String, String>> listUnSettleInfo = new ArrayList<Map<String, String>>();
		
		Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
		PreparedStatement prst = null;
		ResultSet rs = null;
		
		String strSql  = "SELECT settle_order_id, org_id, org_type, settle_belong_date, settle_batch_no, settle_start_time, settle_fee_amount, settle_fee_type, "
						+ " settle_status FROM tbl_settlement_sum_order where settle_status='" + MergPayAndRefundSettleEntity.SETTLE_PROCESSING + "' "
						+ " and settle_belong_date>='"
						+ strSettlStartDay 
						+ "' and settle_belong_date<='"
						+ strSettlEndDay
						+ "' group by org_type, settle_belong_date, settle_batch_no, settle_order_id, org_id order by settle_belong_date, settle_batch_no;";
		try {
			Map<String, String> mapSetlOrderValues = null;
			prst = conn.prepareStatement(strSql);
			rs = prst.executeQuery();
			while (rs.next()) {
				mapSetlOrderValues = new HashMap<String, String>();
				
				// 子商户号
				String strSubMchId = rs.getString("org_id");
				
				// 获取商户所对应的银行账户信息
				Map<String, String> mapArgs = new HashMap<String, String>();
				mapArgs.put("sub_merchant_code", strSubMchId);
				// 银行名称
				String strBankName = getTblFieldValue("merchant_bank_name", "t_merchant", mapArgs);
				// 商户在银行的账户
				String strSubMchBankAcct = getTblFieldValue("merchant_account", "t_merchant", mapArgs);
				
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLEMENT_ORDER_ID, rs.getString("settle_order_id"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.ORG_ID, strSubMchId);
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.ORG_TYPE, rs.getString("org_type"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_BELONG_DATE, rs.getString("settle_belong_date"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_BATCHNO, rs.getString("settle_batch_no"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_START_TIME, rs.getString("settle_start_time"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_FEE_AMOUNT, rs.getString("settle_fee_amount"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_FEE_TYPE, rs.getString("settle_fee_type"));
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.ORG_BANK_NAME, strBankName);
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.ORG_BANK_ACCOUNT, strSubMchBankAcct);
				mapSetlOrderValues.put(GenSetlOrdForNobDownEntity.SETTLE_STATUS, rs.getString("settle_status"));
				
				listUnSettleInfo.add(mapSetlOrderValues);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
		}
		
		return listUnSettleInfo;
	}
	/**
	 * 生成结算单文件，并返回结算单下载的URL地址。
	 * @param listUnSettleInfo
	 * @return
	 */
	private String genSetlFileAndGetDownURL(List<Map<String, String>> listUnSettleInfo) {
		String strSettleFileURL = null;
				
		if (listUnSettleInfo != null && listUnSettleInfo.size() > 0) {
			String strFileGenTime = CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmssSSS");
			String strTotalRecords = String.valueOf(listUnSettleInfo.size());
			double dblTotalAmount = 0.00d;
			String SETL_FEE_TYPE = "USD";
			for (Map<String, String> mapSetlValues : listUnSettleInfo) {
				dblTotalAmount = dblTotalAmount + Double.parseDouble(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_FEE_AMOUNT));
			}
			
			// 为NOB生成下载文件
			String strFileName = strFileGenTime + "_" + CommonTool.formatDoubleToHalfUp(dblTotalAmount, 2, 2) + "_" + SETL_FEE_TYPE + ".csv";
			String strFileFolder = super.getAbsolutFilePath(CommonInfo.SETTLE_FILE_FOLDER_FOR_NOB_DOWN);
			File settleFileFolder = new File(strFileFolder);
			if (!settleFileFolder.exists()) {
				settleFileFolder.mkdir();
			}
			
			String strFileFullName = strFileFolder + "/" + strFileName;
			File settlFile = new File(strFileFullName);
			if (settlFile.exists()) {	// 如果文件存在，则删除旧文件
				settlFile.delete();
			}
			FileWriter fw = null;
			try {
				fw = new FileWriter(settlFile);
				
				String LINE_SEP = "\r\n";
				// 写入文件头信息
				String strHeaderNames = "File_Gen_Time,Total_Records,Total_Settle_Amount,Settle_Fee_Type".concat(LINE_SEP);
				fw.write(strHeaderNames);
				String strHeaderValues = strFileGenTime.concat(",").concat(strTotalRecords).concat(",")
										.concat(CommonTool.formatDoubleToHalfUp(dblTotalAmount, 2, 2)).concat(",").concat(SETL_FEE_TYPE).concat(LINE_SEP);
				fw.write(strHeaderValues);
				
				// 写入文件体信息
				String strBodyNames = "settle_order_id,org_id,org_type,settle_belong_date,settle_batch_no,settle_start_time,settle_fee_amount,settle_fee_type,org_bank_name,org_bank_account,settle_status".concat(LINE_SEP);
				fw.write(strBodyNames);
				StringBuffer sbf = new StringBuffer();
				for (Map<String, String> mapSetlValues : listUnSettleInfo) {
					sbf.delete(0, sbf.length());
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLEMENT_ORDER_ID)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.ORG_ID)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.ORG_TYPE)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_BELONG_DATE)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_BATCHNO)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_START_TIME)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToZero(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_FEE_AMOUNT)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_FEE_TYPE)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.ORG_BANK_NAME)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.ORG_BANK_ACCOUNT)));
					sbf.append(",");
					sbf.append(CommonTool.formatNullStrToSpace(mapSetlValues.get(GenSetlOrdForNobDownEntity.SETTLE_STATUS)));
					sbf.append(LINE_SEP);
					
					String strBodyValue = sbf.toString();
					fw.write(strBodyValue);
				}
				
				// 写入结束符信息
				String strEndFlag = "EOF";
				fw.write(strEndFlag);
				
				// 刷新输出流,确保内容输出成功
				fw.flush();
				
				// 生成结算单文件的存储路径
				strSettleFileURL = CommonInfo.SETTLE_FILE_FOLDER_FOR_NOB_DOWN + "/" + strFileName;
				
			} catch (IOException e) {
				e.printStackTrace();
				strSettleFileURL = null;
			} finally {
				if (fw != null) {
					try {
						fw.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		return strSettleFileURL;
	}
}
