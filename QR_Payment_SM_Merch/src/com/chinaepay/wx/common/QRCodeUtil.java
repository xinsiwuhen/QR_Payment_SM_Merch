package com.chinaepay.wx.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * 二维码工具包。
 * 
 * @author xinwuhen
 */
public class QRCodeUtil {
	private static QRCodeUtil qrCodeUtil = null;

	private QRCodeUtil() {
	}

	public static QRCodeUtil getInstance() {
		if (qrCodeUtil == null) {
			qrCodeUtil = new QRCodeUtil();
		}

		return qrCodeUtil;
	}

	/**
	 * 根据参数生成二维码图片。
	 * 
	 * @param imgCharactCode
	 *            字符编码, 默认为:UTF-8.
	 * @param imgWidth
	 *            图片宽度, 默认为: 300px
	 * @param imgHeight
	 *            图片高度, 默认为: 300px
	 * @param strImgFileFoler
	 * 			    图片存储目录
	 * @param imgFileName
	 *            图片名称(如：myTestQrImg.png)
	 * @param qrContent
	 *            二维码内容
	 * @return 二维码图片的文件对象
	 */
	public File genQrCodeImg(String imgCharactCode, int imgWidth, int imgHeight, String strImgFileFoler, String imgFileName, String qrContent) {
		File imgFullFile = null;
		
		if (strImgFileFoler == null || "".equals(strImgFileFoler) || imgFileName == null || "".equals(imgFileName) 
				|| qrContent == null || "".equals(qrContent)) {
			return imgFullFile;
		}
		
		BitMatrix bitMatrix = null;
		try {
			// 定义二维码参数的哈希映射表
			HashMap<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
			// 编码方式，支持中文
			imgCharactCode = (imgCharactCode == null || "".equals(imgCharactCode) ? "UTF-8" : imgCharactCode);
			hints.put(EncodeHintType.CHARACTER_SET, imgCharactCode);
			// 容错等级(容错等级 L、M、Q、H 其中 L 为最低, H 为最高)
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
			// 二维码边距
			hints.put(EncodeHintType.MARGIN, 1);
			
			// 生成点阵
			imgWidth = (imgWidth <= 0 ? 300 : imgWidth);	// 默认为300px
			imgHeight = (imgHeight <= 0 ? 300 : imgHeight);	// 默认为300px
			
			bitMatrix = new MultiFormatWriter().encode(qrContent, BarcodeFormat.QR_CODE, imgWidth, imgHeight, hints);
			
			// 创建目录
			File fileImgFoler = new File(strImgFileFoler);
			if (!fileImgFoler.exists()) {
				fileImgFoler.mkdir();
			}
			
			// 图片的文件对象
			String strImgFullName = fileImgFoler.getPath() + "/" + imgFileName;
			imgFullFile = new File(strImgFullName);
			
			// 图片扩展名(即：图片格式)
			Path filePath = imgFullFile.toPath();
			String imgFormat = imgFileName.substring(imgFileName.lastIndexOf(".") + 1);
			
			// 输出文件
			MatrixToImageWriter.writeToPath(bitMatrix, imgFormat, filePath);
		} catch (WriterException | IOException e) {
			e.printStackTrace();
			imgFullFile = null;
		}
		
		return imgFullFile;
	}
}
