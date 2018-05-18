<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html lang="UTF-8">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
    <style>
        .error{
            display: flex;
            justify-content: space-around;
            align-items: center;
            width: 80%;
            margin: 0 auto;
        }
        .error-img{width: 200px;height: 200px;}
        .error-img>img{
            width: 100%;}
        .error-content>p{font-size: 30px;color: #595957;}
        .error-content>p:nth-of-type(1){
            color:red;font-size:40px;}
    </style>
</head>
<body>
<div class="error">
    <div class="error-img"><img src="img/error.jpg" alt=""></div>
    <div class="error-content">
        <p>支付失败!</p>
        <p>失败原因:XXXXX</p>
        <p>请联系收银人员处理！</p>
    </div>
</div>

</body>
</html>
