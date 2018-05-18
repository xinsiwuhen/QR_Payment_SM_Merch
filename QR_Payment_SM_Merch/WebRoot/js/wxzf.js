$(function(){
    //填写信息
    $('.infor-sub').click(function(e){
        $('.layer').hide();
        $('.form').hide();
        e.preventDefault();
    })
    //监听内容改变DOMNodeInserted
    $('#div').bind('DOMNodeInserted', function(){
        if($("#div").text()!="" && $("#div").text()>'0'){
            $('.submit').removeClass('active');
            $('.submit').attr('disabled', false);
        }else{
            $('.submit').addClass('active');
            $('.submit').attr('disabled', true);
        }
    });
    //删除监听DOMNodeInserted
    $('#remove').click('DOMNodeInserted',function () {
        if($("#div").text()!="" && $("#div").text()>'0'){
            $('.submit').addClass('active');
            $('.submit').attr('disabled', true);
        }
    });


    $('#div').trigger('DOMNodeInserted');

    $('.shuru').click(function(e){
        $('.layer-content').animate({
            bottom: 0
        }, 200)
        e.stopPropagation();
    })
    $('.wrap').click(function(){
        $('.layer-content').animate({
            bottom: '-200px'
        }, 200)
    })

    $('.form_edit .num').click(function(){
        var oDiv = document.getElementById("div");
        oDiv.innerHTML += this.innerHTML;
    })
    $('#remove').click(function(){
        var oDiv = document.getElementById("div");
        var oDivHtml = oDiv.innerHTML;
        oDiv.innerHTML = oDivHtml.substring(0,oDivHtml.length-1);
    })
})