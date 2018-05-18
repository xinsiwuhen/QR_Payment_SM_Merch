package com.chinaepay.wx.servlet;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.JsonRespObj;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.common.QRCodeUtil;

import net.sf.json.JSONObject;

/**
 * 生成二维码。
 * @author xinwuhen
 */
public class GenQRCodeServlet extends ExtendsHttpServlet {

	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		JsonRespObj respObj = new JsonRespObj();
		// 设置应答返回结果(0：失败  1：成功)
		String strGenResult = "0";
		String strReturnMsg = "";
		Map<String, String> mapSubMchImg = null;
		try {
			request.setCharacterEncoding("UTF-8");
			
			// 用户扫描二维码, 在获取用户的OpenId之前, 需获取CODE码
			String strWxAuthCodeURL = CommonTool.getAbsolutWebURL(request, true) + "/" + CommonInfo.GET_WX_AUTH_CODE_URL;
			System.out.println("strWxAuthCodeURL = " + strWxAuthCodeURL);
			
			// 取得终端商户ID
			String sub_mch_id = CommonTool.urlDecodeUTF8(request.getParameter("sub_mch_id"));
			// 二维码内容
			String strQRCodeContent = strWxAuthCodeURL + "?sub_mch_id=" + sub_mch_id;
			// 生成二维码并将对应信息更新到数据表
			boolean blnGenRst = genQRAndUpToTbl(strQRCodeContent, sub_mch_id);
			System.out.println("blnGenRst = " + blnGenRst);
			
			// 取得二维码存储路径
			Map<String, String> mapArgs = new HashMap<String, String>();
			mapArgs.put("sub_mch_id", sub_mch_id);
			String strQRFilePath = super.getTblFieldValue("qr_png_folder", "tbl_qrcode_info", mapArgs);
	    	String strWebAppName = CommonTool.getWebAppName(this.getServletContext());
	    	System.out.println("strWebAppName = " + strWebAppName);
	    	String strPrePath = CommonTool.getAbsolutWebAppPath(this.getClass(), System.getProperty("file.separator")).replaceAll(strWebAppName, "");
	    	System.out.println("strPrePath = " + strPrePath);
	    	String strQRPngFolder = strQRFilePath.replace(strPrePath, "");
	    	System.out.println("strQRPngFolder = " + strQRPngFolder);
			String strImgName = super.getTblFieldValue("qr_png_name", "tbl_qrcode_info", mapArgs);
			
			// 设置应答返回结果(0：失败  1：成功)
			if (blnGenRst) {
				strGenResult = "1";
				strReturnMsg = "生成二维码成功!";
				mapSubMchImg = new HashMap<String, String>();
				mapSubMchImg.put(sub_mch_id, strQRPngFolder + "/" + strImgName);
			} else {
				strGenResult = "0";
				strReturnMsg = "生成二维码失败!";
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			strGenResult = "0";
			strReturnMsg = "生成二维码失败!";
		}
		
		respObj.setRespCode(strGenResult);
		respObj.setRespMsg(strReturnMsg);
		respObj.setRespObj(mapSubMchImg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		this.doGet(request, response);
	}
	
	/**
	 * 生成二维码图片并下载至对应目录，同时将图片存储路径信息更新到数据表。
	 * @param strQRCodeContent
	 * @return
	 */
	private boolean genQRAndUpToTbl(String strQRCodeContent, String strSubMchId) {
		if (strQRCodeContent == null || "".equals(strQRCodeContent) || strSubMchId == null || "".equals(strSubMchId)) {
			return false;
		}
		
		/** 根据参数生成二维码图片 **/
		String strQRPngFolder = super.getAbsolutFilePath(CommonInfo.QR_IMG_FOLDER);
		String strQRPngName = strSubMchId + ".png";
		File qrImgFile = QRCodeUtil.getInstance().genQrCodeImg("UTF-8", 300, 300, strQRPngFolder, strQRPngName, strQRCodeContent);
		System.out.println("qrImgFile = " + qrImgFile);
		
    	/** 将图片存储路径信息更新到数据表 **/
    	MysqlConnectionPool mysqlConnPool = MysqlConnectionPool.getInstance();
		Connection conn = mysqlConnPool.getConnection(false);
		PreparedStatement pstat = null;
		String strUpdateSql = "replace into tbl_qrcode_info(sub_mch_id, qr_code_content, qr_png_folder, qr_png_name, gen_time) values('" 
								+ strSubMchId + "','" 
								+ strQRCodeContent + "','" 
								+ strQRPngFolder + "','" 
								+ strQRPngName + "','"
								+ CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss") + "');";
		try {
			pstat = conn.prepareStatement(strUpdateSql);
			pstat.executeUpdate();
			
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(pstat, conn);
		}
		
    	return true;
	}
}