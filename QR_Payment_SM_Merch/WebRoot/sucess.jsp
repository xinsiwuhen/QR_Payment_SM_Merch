<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>
<html lang="UTF-8">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
    <style>
        .success{
            display: flex;
            justify-content: space-around;
            align-items: center;
            width: 80%;
            margin: 0 auto;
        }
        .success-img{width: 200px;height: 200px;}
        .success-img>img{
            width: 100%;}
        .success-content>p{font-size: 30px;color: #595957;}
        .success-content>p:nth-of-type(1){
            color: green;font-size:40px;}
    </style>
</head>
<body>
    <div class="success">
        <div class="success-img"><img src="img/success.jpg" alt=""></div>
        <div class="success-content">
            <p>支付成功!</p>
            <p>商户名称:XXXXX</p>
            <p>支付金额：$50.00</p>
        </div>
    </div>

</body>
</html>
