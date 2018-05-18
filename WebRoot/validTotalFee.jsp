<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%><%@ page language="java" import="java.util.*,com.chinaepay.wx.entity.CommunicateEntity" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>
<!DOCTYPE HTML>
<html>
	<head>
		<meta charset="UTF-8"/>
		<meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no" />
		<meta http-equiv="Cache-Control" content="no-store" />
		<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1" />
		<meta name="description" content="不超过150个字符" />
		<meta name="keywords" content="" />
		<link rel="stylesheet" href="css/wxzf.css">
		<title>微信支付</title>
	</head>
	<body>
	<div class="header">
		<div class="all_w ">
			<div class="gofh"> <a href="#" onclick="returnWxWindow()"><img src="img/jt_03.jpg" ></a> </div>
			<div class="ttwenz">
				<div id="return" onclick="returnWxWindow()"><h5>返回</h5></div>
				<h4>在线支付</h4>
			</div>
		</div>
	</div>
	<div class="wrap">
			<form class="edit_cash" id="genProductForm" method="post" action="./payment/payOrderServlet" onsubmit="return genProductOrder();">
				<p>消费总额</p>
				<div class="shuru">
					<span>&yen;</span>
					<div id="div"></div>
					<input type="hidden" name="sub_mch_id" value="<%=request.getParameter("sub_mch_id")%>">
					<input type="hidden" id="totalFee" name="total_fee" value="">
					<input type="hidden" id="body" name="body" value="HarvestPay">
					<input type="hidden" id="openid" name="openid" value="<%=request.getParameter("openid")%>">
					<input type="hidden" id="user_pay_type" name="user_pay_type" value="<%=CommunicateEntity.PAY_TYPE_SCAN_QR%>">
				</div>
				<p>可询问工作人员应缴费用总额</p>
				<!--input type="submit" value="支付" class="submit"/-->
				<!--input type="submit" value="支付" class="submit" onclick="genProductOrder()"/-->
				<input type="submit" value="支付" class="submit"/>
			</form>
		</div>
	<div class="layer"></div>
	<div class="layer-content">
			<div class="form_edit clearfix">
				<div class="num">1</div>
				<div class="num">2</div>
				<div class="num">3</div>
				<div class="num">4</div>
				<div class="num">5</div>
				<div class="num">6</div>
				<div class="num">7</div>
				<div class="num">8</div>
				<div class="num">9</div>
				<div class="num">.</div>
				<div class="num">0</div>
				<div id="remove">删除</div>
			</div>
		</div>
	<script src="js/jquery-2.1.3.min.js"></script>
	<script src="js/wxzf.js"></script>
	<script type="text/javascript">
		// 创建商品订单并调用微信端的支付接口
		function genProductOrder() {
			var vTotalFee = document.getElementById('div').innerHTML;
			
			var g = /^\d+(?:\.\d{1,4})?$/;
			if(g.test(vTotalFee) == false){
		    	//alert('输入金额格式错误!');
		    	document.getElementById('div').innerHTML = "";
		    	return false;
			} else {
				document.getElementById("totalFee").value = vTotalFee;
				document.forms["genProductForm"].submit();
				return true;
			}
		}
		
		// 关闭当窗口，返回腾讯聊天窗口
		function returnWxWindow() {
			var ua = navigator.userAgent.toLowerCase();
       		if(ua.match(/MicroMessenger/i)=="micromessenger") {      
         		WeixinJSBridge.call('closeWindow');      
      		} else {      
          		window.close();      
      		}
		}
	</script>
	</body>
</html>
