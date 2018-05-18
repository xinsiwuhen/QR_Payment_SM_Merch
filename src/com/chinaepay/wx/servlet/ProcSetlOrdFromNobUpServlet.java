package com.chinaepay.wx.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.chinaepay.wx.common.CommonInfo;
import com.chinaepay.wx.common.CommonTool;
import com.chinaepay.wx.common.MysqlConnectionPool;
import com.chinaepay.wx.common.JsonRespObj;
import com.chinaepay.wx.entity.ProcSetlOrdFromNobUpEntity;

import net.sf.json.JSONObject;

/**
 * 处理NOB上传的结算单文件，并将处理结果更新到后台数据库。
 * @author xinwuhen
 */
public class ProcSetlOrdFromNobUpServlet extends ProcSettleOrderServlet {
	private static final String SETTLE_FILE_HEADER_KEY = "HeadInfo";
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		JsonRespObj respObj = new JsonRespObj();
		String strProcResult = "1";
		String strReturnMsg = "";

		// 读取NOB侧返回的结算单内容，并将其存储在固定目录下
		List<File> listUploadFiles = this.readAndStoreNobStlOrder(request, response);
		System.out.println("listUploadFiles = " + listUploadFiles);
		if (listUploadFiles == null || listUploadFiles.size() == 0) {
			strProcResult = "0";
			strReturnMsg = "上传文件失败(未选择任何文件、文件名为空或是文件大小为0)！";
		} else {
			for (File uploadFile : listUploadFiles) {
				System.out.println("uploadFile.AbsolutePath = " + uploadFile.getAbsolutePath());
				System.out.println("uploadFile.Name = " + uploadFile.getName());
				
				// 读取NOB侧返回的结算单内容
				Map<String, Map<String, String>> mapSetlInfo = this.getMapSetlInfos(uploadFile);
				System.out.println("mapSetlInfo = " + mapSetlInfo);
				
				// 校验结算单信息是否正确
				boolean blnValidSetlRst = validNobSetlOrderInfo(mapSetlInfo);
				System.out.println(">>.blnValidSetlRst = " + blnValidSetlRst);
				if (!blnValidSetlRst) {
					strProcResult = "0";
					strReturnMsg = "文件头内的总结算金额或总记录数，与文件体内的实际内容不一致！";
				} else {
					// 更新后台数据库中的结算单状态
					boolean blnUpRst = updateNobSetlRstToTbl(mapSetlInfo);
					System.out.println(">>>.blnUpRst = " + blnUpRst);
					
					if (blnUpRst) {
						strProcResult = "1";
						strReturnMsg = "上传文件并且更新结算单状态成功！";
					} else {
						strProcResult = "0";
						strReturnMsg = "结算单处理失败，请检查除结算单状态列以外的其它信息，是否与原文件保持一致！";
					}
				}
			}
		}
		
		System.out.println("strProcResult = " + strProcResult);
		System.out.println("strReturnMsg = " + strReturnMsg);
		
		// 访问跨域访问，实现客户端请求时的AJAX读取JSON内容
		response.setHeader("Access-Control-Allow-Origin", "*");
		
		respObj.setRespCode(strProcResult);
		respObj.setRespMsg(strReturnMsg);
		JSONObject jsonObj = JSONObject.fromObject(respObj);
		super.returnJsonObj(response, jsonObj);
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		doGet(request, response);
	}
	
	/**
	 * 读取NOB侧返回的结算单内容，并将其存储在固定目录下。
	 * @param request
	 * @param response
	 * @return
	 */
	private List<File> readAndStoreNobStlOrder(HttpServletRequest request, HttpServletResponse response) {
		List<File> listUploadFiles = new ArrayList<File>();
		try {
			// 结算单文件保存目录
			String strSetlFilePath = super.getAbsolutFilePath(CommonInfo.SETTLE_FILE_FOLDER_FOR_NOB_UP);
			
			DiskFileItemFactory diskFactory = new DiskFileItemFactory();
            // threshold 极限、临界值，即硬盘缓存 1M
            diskFactory.setSizeThreshold(10 * 1024);
            // repository 贮藏室，即临时文件目录
            diskFactory.setRepository(new File(strSetlFilePath));
            ServletFileUpload upload = new ServletFileUpload(diskFactory);
            //防止乱码
            upload.setHeaderEncoding("UTF-8");
            // 设置允许上传的最大文件大小 4M
            upload.setSizeMax(4 * 1024 * 1024);
            // 解析HTTP请求消息头
            List<FileItem> fileItems = upload.parseRequest(request);
            
            for (FileItem fileItem : fileItems) {
            	if (fileItem.isFormField()) {	// 是表单内的属性
            		processFormField(fileItem);	//处理表单内容
            	} else {	// 是上传的文件
            		File fileUploaded = processUploadFile(strSetlFilePath, fileItem);	//处理上传的文件
            		if (fileUploaded != null) {
            			listUploadFiles.add(fileUploaded);
            		}
            	}
            }
		} catch (FileUploadException e) {
			e.printStackTrace();
		} finally {
			
		}
		
		return listUploadFiles;
	}
	
	/**
	 * 处理表单内的属性值。
	 * @param item
	 * @param pw
	 */
	private void processFormField(FileItem item) {
//		String name = item.getFieldName();
//		if (name.equals("stuid")) {
//			studentid = item.getString();
//		} else if (name.equals("workid")) {
//			workid = item.getString();
//		}
	}
	
	private File processUploadFile(String strSetlFilePath, FileItem item) {
		File fileUploaded = null;
		
		// 客户端上传时的文件合路径名称
		String strFileFullName = item.getName();
		System.out.println("strFileFullName = " + strFileFullName);
		int iFileIndex = strFileFullName.lastIndexOf("\\");
		String strFileName = strFileFullName.substring(iFileIndex + 1, strFileFullName.length());
		System.out.println("strFileName = " + strFileName);
		long lngFileSize = item.getSize();
		if ("".equals(strFileName) || lngFileSize == 0) {
			System.out.println("文件名为空或是文件大小为0.");
			fileUploaded = null;
		} else {
			String strServerFileFullName =  strSetlFilePath + "/" + strFileName;
			try {
				File uploadFile = new File(strServerFileFullName);
				if (uploadFile.exists()) {	// 删除旧的文件
					uploadFile.delete();
				}
				
				item.write(uploadFile);
				fileUploaded = uploadFile;
			} catch (Exception e) {
				e.printStackTrace();
				fileUploaded = null;
			}
		}
		
		return fileUploaded;
	}
	
	/**
	 * 读取NO侧返回的结算单内容。
	 * @param nobSetlFile
	 * @return	Map<String, Map<String, String>> 结构中包含头部信息以及包体信息，具体举例如下：
	 * {HeadInfo:{File_Gen_Time:20180514135123, Total_Records:3, Total_Settle_Amount:0.5, Settle_Fee_Type:USD}}
	 * {a565fg4vcsw25s3g2tiqgpn2jehycgwh:{org_id:212322593, org_type:Submch, settle_belong_date:20180509, settle_batch_no:20180509_0001, 
	 * settle_start_time:20180513234455, settle_fee_amount:0.03, settle_fee_type:USD, settle_bank_type:NOB, settle_bank_account:683733456336536, 
	 * settle_status:1}}
	 */
	private Map<String, Map<String, String>> getMapSetlInfos(File nobSetlFile) {
		Map<String, Map<String, String>> mapSetlInfo = new HashMap<String, Map<String, String>>();
		if (nobSetlFile == null) {
			return mapSetlInfo;
		}
		
		FileReader reader = null;
        BufferedReader buffReader = null;
		try {
			reader = new FileReader(nobSetlFile);
			buffReader = new BufferedReader(reader);
			
			String strLine = null;
			while ((strLine = buffReader.readLine()) != null) {
				if (strLine.toLowerCase().startsWith("File_Gen_Time".toLowerCase())) {	// 获取文件头信息
					Map<String, String> mapHeadInfo = new HashMap<String, String>();
					String[] strHeadNames = strLine.split(",");	// 文件头内容中的字段名称信息
					
					strLine = buffReader.readLine(); 	// 文件头内容中的字段值信息
					if (strLine != null) {
						String[] strHeadValues = strLine.split(",");
						for (int i = 0; i < strHeadNames.length; i++) {
							String strHeadValue = "";
							if (i < strHeadValues.length) {
								strHeadValue = strHeadValues[i];
							}
							mapHeadInfo.put(strHeadNames[i], CommonTool.formatNullStrToSpace(strHeadValue));
						}
					}
					mapSetlInfo.put(SETTLE_FILE_HEADER_KEY, mapHeadInfo);		
				} else if (strLine.toLowerCase().startsWith("settle_order_id".toLowerCase())) {	// 获取文件体信息
					Map<String, String> mapBodyInfo = null;
					String[] strBodyNames = strLine.split(",");	// 文件体内容中的字段名称信息
					
					while ((strLine = buffReader.readLine()) != null && !strLine.startsWith("EOF")) {	// 文件体内容中的字段值信息
						mapBodyInfo = new HashMap<String, String>();
						String[] strBodyValues = strLine.split(",");
						String strSetlOrdId = null;
						for (int j = 0; j < strBodyValues.length; j++) {
							if (j == 0) {
								strSetlOrdId = strBodyValues[j];
							} else {
								mapBodyInfo.put(strBodyNames[j], strBodyValues[j]);
							}
						}
						mapSetlInfo.put(strSetlOrdId, mapBodyInfo);
					}
				} else if (strLine.startsWith("EOF")) {
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (buffReader != null) {
				try {
					buffReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return mapSetlInfo;
	}
	
	/**
	 * 校验结算单信息是否正确。
	 * @param mapSetlInfo
	 * @return
	 */
	private boolean validNobSetlOrderInfo(Map<String, Map<String, String>> mapSetlInfo) {
		boolean blnValdSetlRst = false;
		
		Map<String, String> mapHeader = mapSetlInfo.get(SETTLE_FILE_HEADER_KEY);
		if (mapHeader != null) {
			String strTotalRecords = mapHeader.get("Total_Records");
			String strTotalSetlAmount = mapHeader.get("Total_Settle_Amount");
			System.out.println("Double.parseDouble(strTotalSetlAmount) = " + Double.parseDouble(strTotalSetlAmount));
			
			String[] strKeys = mapSetlInfo.keySet().toArray(new String[0]);
			double dblSetlFeeAmount = 0d;
			for (String strKey : strKeys) {
				if (!SETTLE_FILE_HEADER_KEY.equals(strKey)) {
					String strSetlFeeAmt = mapSetlInfo.get(strKey).get("settle_fee_amount");
					System.out.println("strSetlFeeAmt = " + strSetlFeeAmt);
					
					dblSetlFeeAmount = dblSetlFeeAmount + Double.parseDouble(strSetlFeeAmt);
				}
			}
			
			
			System.out.println("dblSetlFeeAmount = " + dblSetlFeeAmount);
			System.out.println("strTotalRecords = " + strTotalRecords);
			
			if (Double.parseDouble(strTotalSetlAmount) == Double.parseDouble(CommonTool.formatDoubleToHalfUp(dblSetlFeeAmount, 2, 2)) && Integer.parseInt(strTotalRecords) == (mapSetlInfo.size() - 1)) {
				blnValdSetlRst = true;
			}
		}
		
		return blnValdSetlRst;
	}
	
	/**
	 * 更新后台数据库中的结算单状态。
	 * @param mapSetlInfo
	 */
	private boolean updateNobSetlRstToTbl(Map<String, Map<String, String>> mapSetlInfo) {
		boolean blnUpAllInfoRst = false;
		Connection conn = MysqlConnectionPool.getInstance().getConnection(false);
		
		try {
			// 1.更新最终结算单表
			boolean blnUpFinalSetlRst = this.updateFinalSettleOrderInfo(mapSetlInfo, conn);
			System.out.println("blnUpFinalSetlRst = " + blnUpFinalSetlRst);
			
			// 2.更新中间/明细结算单表(含支付明细表、退款明细表)
			if (blnUpFinalSetlRst) {
				// 2.1 更新支付单结算中间表
				this.updatePaySetlOrderInfo(mapSetlInfo, conn, "tbl_pay_settle_detail_order");
				
				// 2.2 更新退款单结算中间表
				this.updatePaySetlOrderInfo(mapSetlInfo, conn, "tbl_refund_settle_detail_order");
				
				// 所有的校验通过，最后进行数据提交
				conn.commit();
				blnUpAllInfoRst = true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			blnUpAllInfoRst = false;
			try {
				conn.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		} finally {
			MysqlConnectionPool.getInstance().releaseConnInfo(conn);
		}
		
		return blnUpAllInfoRst;
	}
	
	/**
	 * 根据NOB上传的结算单文件，校验文件内所有的记录是否与更新最终结算单表一致（即：结算单ID、结算金额是否一致）。
	 * @param mapSetlInfo
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	private boolean updateFinalSettleOrderInfo(Map<String, Map<String, String>> mapSetlInfo, Connection conn) throws SQLException {
		boolean blnUpFinalSetlRst = true;
		String strSql = "update tbl_settlement_sum_order set settle_status=?, settle_end_time=? "
						+ " where settle_order_id=?;";
		PreparedStatement prst = conn.prepareStatement(strSql);
		
		String[] strKeys = mapSetlInfo.keySet().toArray(new String[0]);
		for (String strKey : strKeys) {
			if (!SETTLE_FILE_HEADER_KEY.equals(strKey)) {
				String strSetlOrderId = strKey;
				Map<String, String> mapSetlBodyValues = mapSetlInfo.get(strSetlOrderId);
				
				// 校验数据库后是否包含此条结算单（确保：结算单号、结算金额一致）
				Map<String, String> mapArgs = new HashMap<String, String>();
				mapArgs.put("settle_order_id", strSetlOrderId);
				mapArgs.put("settle_fee_amount", CommonTool.formatNullStrToSpace(mapSetlBodyValues.get(ProcSetlOrdFromNobUpEntity.SETTLE_FEE_AMOUNT)));
				String strTblSetlOrderId = getTblFieldValue("settle_order_id", "tbl_settlement_sum_order", mapArgs);
				System.out.println("strTblSetlOrderId = " + strTblSetlOrderId);
				
				if (strTblSetlOrderId == null || "".equals(strTblSetlOrderId)) {
					blnUpFinalSetlRst = false;
					break;
				} else {
					prst.setString(1, CommonTool.formatNullStrToSpace(mapSetlBodyValues.get(ProcSetlOrdFromNobUpEntity.SETTLE_STATUS)));
					prst.setString(2, CommonTool.getFormatDateStr(new Date(), "yyyyMMddHHmmss"));
					prst.setString(3, strSetlOrderId);
					prst.addBatch();
				}
			}
		}
		
		System.out.println("blnUpFinalSetlRst = " + blnUpFinalSetlRst);
		if (blnUpFinalSetlRst) {	// NOB上传的结算单文件内的记录，全部校验通过
			prst.executeBatch();
		}
		
		return blnUpFinalSetlRst;
	}
	
	/**
	 * 更新支付单与退款单结算中间表中的结算状态。
	 * @param mapSetlInfo
	 * @param conn
	 * @return
	 * @throws SQLException 
	 */
	private void updatePaySetlOrderInfo(Map<String, Map<String, String>> mapSetlInfo, Connection conn, String strTblName) throws SQLException {
		String[] strKeys = mapSetlInfo.keySet().toArray(new String[0]);
		PreparedStatement prst = null;
		
		for (String strKey : strKeys) {
			if (!SETTLE_FILE_HEADER_KEY.equals(strKey)) {
				String strSetlOrderId = strKey;
				Map<String, String> mapSetlBodyValues = mapSetlInfo.get(strSetlOrderId);
				String strSql = "update " + strTblName + " set COLUMN_SETTLE_STATUS='" 
							+ mapSetlBodyValues.get(ProcSetlOrdFromNobUpEntity.SETTLE_STATUS) 
							+ "' where COLUMN_SETTLE_ORDER_ID='" 
							+ strSetlOrderId
							+ "';";
				
				System.out.println("$$$strSql = " + strSql);
				
				String strOrgType = mapSetlBodyValues.get(ProcSetlOrdFromNobUpEntity.ORG_TYPE);
				if (ProcSetlOrdFromNobUpEntity.ORG_TYPE_NOB.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为NOB
					strSql = strSql.replace("COLUMN_SETTLE_STATUS", "nob_settle_status").replace("COLUMN_SETTLE_ORDER_ID", "nob_settle_order_id");
				} else if (ProcSetlOrdFromNobUpEntity.ORG_TYPE_HARVEST.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Harvest
					strSql = strSql.replace("COLUMN_SETTLE_STATUS", "har_settle_status").replace("COLUMN_SETTLE_ORDER_ID", "har_settle_order_id");
				} else if (ProcSetlOrdFromNobUpEntity.ORG_TYPE_AGENT.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Agent
					strSql = strSql.replace("COLUMN_SETTLE_STATUS", "agen_settle_status").replace("COLUMN_SETTLE_ORDER_ID", "agen_settle_order_id");
				} else if (ProcSetlOrdFromNobUpEntity.ORG_TYPE_SUB_MCH.equals(CommonTool.formatNullStrToSpace(strOrgType))) {	// 机构类型为Submch
					strSql = strSql.replace("COLUMN_SETTLE_STATUS", "submch_settle_status").replace("COLUMN_SETTLE_ORDER_ID", "submch_settle_order_id");
				}
				
				prst = conn.prepareStatement(strSql);
				prst.executeUpdate();
				prst.close();
			}
		}
	}
}
