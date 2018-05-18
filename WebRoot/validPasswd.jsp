<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
    <base href="<%=basePath%>">
    <title></title>
	<meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="">
	<meta http-equiv="description" content="">
	<script src="http://res.wx.qq.com/open/js/jweixin-1.0.0.js"></script>
	<script type="text/javascript">
		function onBridgeReady() {
		   WeixinJSBridge.invoke(
		       'getBrandWCPayRequest', {
		           "appId":"<%=request.getParameter("appId")%>",     				//公众号名称，由商户传入     
		           "timeStamp":"<%=request.getParameter("timeStamp")%>",        	//时间戳，自1970年以来的秒数     
		           "nonceStr": "<%=request.getParameter("nonceStr")%>", 			//随机串     
		           "package": "prepay_id=<%=request.getParameter("prepay_id")%>",   //预付单号  
		           "signType":"<%=request.getParameter("signType")%>",          	//微信签名方式：     
		           "paySign":"<%=request.getParameter("paySign")%>" 				//微信签名 
		       },
		       function(res) {
		           if(res.err_msg == "get_brand_wcpay_request:ok" ) {
		           		var ua = navigator.userAgent.toLowerCase();
		           		if(ua.match(/MicroMessenger/i)=="micromessenger") {      
               				WeixinJSBridge.call('closeWindow');      
            			} else {      
                			window.close();      
            			}
		           }     // 使用以上方式判断前端返回,微信团队郑重提示：res.err_msg将在用户支付成功后返回    ok，但并不保证它绝对可靠。 
		           else if (res.err_msg == "get_brand_wcpay_request:fail") {
                         alert(JSON.stringify(res));
                   }
		       }
		   ); 
		}
		
		function callpay() {
		 	if (typeof WeixinJSBridge == "undefined") {
			   if (document.addEventListener){
			       document.addEventListener('WeixinJSBridgeReady',onBridgeReady,false);
			   } else if (document.attachEvent){
			       document.attachEvent('WeixinJSBridgeReady',onBridgeReady); 
			       document.attachEvent('onWeixinJSBridgeReady',onBridgeReady);
			   }
			} else {
			   onBridgeReady();
			}
		}
	</script>
  </head>
  
  <body onload="callpay()"></body>
</html>
