package com.chinaepay.wx.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.entity.CommunicateEntity;

public abstract class TransControllerServlet extends CommControllerServlet {
	
	/**
	 * 插入由终端商户发起的初始化订单信息到数据库。
	 */
	public abstract boolean insertOrderInfoToTbl(Map<String, String> mapArgs);
	
	/**
	 * 更新易付通后台与腾讯后台交互后的结果到对应的数据库表。
	 */
	public abstract boolean updateOrderRstToTbl(Map<String, String> mapArgs);
	
	/**
	 * 校验商户是否有支付、退款、关闭订单等权限。
	 */
	public boolean validSubMchIsUsable(String strSubMerchId) {
		boolean blnValSubMchIsUsable = false;
		Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
		PreparedStatement prst = null;
		ResultSet rs = null;
		try {
			String strSql = "select * from t_merchant where sub_merchant_code='" + CommonTool.formatNullStrToSpace(strSubMerchId) + "';";
			prst = conn.prepareStatement(strSql);
			rs = prst.executeQuery();
			if (rs.next()) {
				int iAuditStatus = rs.getInt("audit_status");
				int iStatus = rs.getInt("status");
				int iAccountStatus = rs.getInt("account_status");
				String strDelFlag = rs.getString("del_flag");
				
				System.out.println("strSql = " + strSql);
				System.out.println("iAuditStatus = " + iAuditStatus);
				System.out.println("iStatus = " + iStatus);
				System.out.println("iAccountStatus = " + iAccountStatus);
				System.out.println("strDelFlag = " + strDelFlag);
				
				if (iAuditStatus == Integer.valueOf(CommunicateEntity.AUDIT_STATUS_OK) // 审核状态   1 待审核 2美唯审核 3银行审核 4审核通过  -1审核不通过
						&& iStatus == Integer.valueOf(CommunicateEntity.MERCHANT_STATUS_OK)	// 商户状态 1 启用 2禁用
						&& iAccountStatus == Integer.valueOf(CommunicateEntity.ACCOUNT_STATUS_OK)	// 账户状态  1正常 2 冻结
						&& CommonTool.formatNullStrToSpace(strDelFlag).equals(CommunicateEntity.ACCOUNT_DELETED_NG)) {	// 1:已删除  0:未删除
					blnValSubMchIsUsable = true;
				}
			}
		} catch (SQLException se) {
			se.printStackTrace();
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
		}
		
		return blnValSubMchIsUsable;
	}
	
	/**
	 * 通过子商户ID，获取当前处于签到状态的店员ID。
	 * @param strTermMchId
	 * @return
	 */
	public String getSignedAssitantid(String strTermMchId) {
		String strSignedAssitantId = "";
		Connection conn = MysqlConnectionPool.getInstance().getConnection(true);
		PreparedStatement prst = null;
		ResultSet rs = null;
		try {
			String strSql = "select * from t_assistant_sign where merchant_id='" + strTermMchId + "' and status=1 and del_flag='0';"; // status为1：在线；2：离线。del_flag为1:已删除 ; 0：未删除。 
			conn = MysqlConnectionPool.getInstance().getConnection(true);
			prst = conn.prepareStatement(strSql);
			rs = prst.executeQuery();
			if (rs.next()) {
				strSignedAssitantId = rs.getString("assistant_id");
			}
		} catch(SQLException se) {
			se.printStackTrace();
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(rs, prst, conn);
		}
		
		return strSignedAssitantId;
	}
}
