package com.xm.wechat_robot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.xm.wechat_robot.constance.ActionTypeEnum;
import com.xm.wechat_robot.controller.WxActionWss;
import com.xm.wechat_robot.mapper.WcMachineCodeMapper;
import com.xm.wechat_robot.mapper.WcWxAccountMapper;
import com.xm.wechat_robot.mapper.WcWxMsgMapper;
import com.xm.wechat_robot.serialize.bo.action.ActionMsg;
import com.xm.wechat_robot.serialize.bo.action.UserMsgActionMsg;
import com.xm.wechat_robot.serialize.bo.client.UserMsg;
import com.xm.wechat_robot.serialize.bo.client.WxClientMsg;
import com.xm.wechat_robot.serialize.entity.WcMachineCodeEntity;
import com.xm.wechat_robot.serialize.entity.WcWxAccountEntity;
import com.xm.wechat_robot.serialize.entity.WcWxMsgEntity;
import com.xm.wechat_robot.serialize.vo.WcWxMsgVo;
import com.xm.wechat_robot.service.WxUserMsgService;
import com.xm.wechat_robot.util.PageBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service("wxUserMsgService")
public class WxUserMsgServiceImpl implements WxUserMsgService {

    @Autowired
    private WcWxMsgMapper wcWxMsgMapper;
    @Autowired
    private WcWxAccountMapper wcWxAccountMapper;
    @Autowired
    private WcMachineCodeMapper wcMachineCodeMapper;

    @Override
    public void receiveMsg(String accountWxid, WxClientMsg<UserMsg> userMsg) {
        WcWxMsgEntity entity = new WcWxMsgEntity();
        entity.setAccountWxid(accountWxid);
        entity.setMsgFrom(userMsg.getValue().getMsgFrom());
        entity.setChatRoom(userMsg.getValue().getChatRoom());
        entity.setChatRoomWxid(userMsg.getValue().getChatRoomWxid());
        entity.setTargetUserWxid(userMsg.getValue().getTargetUserWxid());
        entity.setType(userMsg.getValue().getType());
        entity.setMsg(Base64.decodeStr(userMsg.getValue().getMsgInfo().getMsg()));
        entity.setMsgResource(Base64.decodeStr(userMsg.getValue().getMsgInfo().getMsgResource()));
        entity.setCreateTime(new Date());
        wcWxMsgMapper.insertSelective(entity);
    }

    @Override
    public void httpCallBack(Integer accountId, WxClientMsg<UserMsg> userMsg) {
        WcMachineCodeEntity machineCodeEntity = wcMachineCodeMapper.selectByPrimaryKey(userMsg.getMachineId());
        if(StrUtil.isBlank(machineCodeEntity.getMsgCallBackUrl())) {
            log.warn("客户端：{} 尚未配置微信消息回调地址", machineCodeEntity.getMachineCode());
            return;
        }
        ActionMsg actionMsg = praseToVo(machineCodeEntity,accountId,userMsg);
        HttpUtil.createPost(machineCodeEntity.getMsgCallBackUrl())
                .contentType("application/json")
                .charset("utf-8")
                .body(JSON.toJSONString(actionMsg))
                .execute();
    }

    @Override
    public void wsCallBack(Integer accountId, WxClientMsg<UserMsg> userMsg) {
        WcMachineCodeEntity machineCodeEntity = wcMachineCodeMapper.selectByPrimaryKey(userMsg.getMachineId());
        ActionMsg actionMsg = praseToVo(machineCodeEntity,accountId,userMsg);
        WxActionWss.sendActionMsg(actionMsg);
    }

    private ActionMsg praseToVo(WcMachineCodeEntity machineCodeEntity,Integer accountId, WxClientMsg<UserMsg> userMsg){
        WcWxAccountEntity accountEntity = wcWxAccountMapper.selectByPrimaryKey(accountId);
        UserMsgActionMsg userMsgAction = new UserMsgActionMsg();
        userMsgAction.setMachineCode(machineCodeEntity.getMachineCode());
        userMsgAction.setClientId(accountEntity.getTcpId());
        userMsgAction.setAccountWxid(accountEntity.getWxid());
        userMsgAction.setIsGroupMsg(userMsg.getValue().getChatRoom().equals(1));
        userMsgAction.setGroupWxid(userMsg.getValue().getChatRoomWxid());
        userMsgAction.setTargetUserWxid(userMsg.getValue().getTargetUserWxid());
        userMsgAction.setMsgType(userMsg.getValue().getType());
        userMsgAction.setMsgFrom(userMsg.getValue().getMsgFrom());
        userMsgAction.setAtUserListWxids(praseAtWxid(Base64.decodeStr(userMsg.getValue().getMsgInfo().getMsgResource())));
        userMsgAction.setMsgBody(userMsg.getValue().getMsgInfo().getMsg());

        ActionMsg<UserMsgActionMsg> actionMsg = new ActionMsg<>();
        actionMsg.setMachineCode(machineCodeEntity.getMachineCode());
        actionMsg.setClientId(userMsg.getTcpId());
        actionMsg.setType(ActionTypeEnum.ON_USER_MSG.getType());
        actionMsg.setAction(userMsgAction);
        return actionMsg;
    }

    @Override
    public PageBean<WcWxMsgEntity> getUserMsg(String accountWxid, String groupWxid, String targetUserWxid,Integer msgFrom,Integer pageNum,Integer pageSize) {
        WcWxMsgEntity record = new WcWxMsgEntity();
        record.setAccountWxid(accountWxid);
        record.setChatRoomWxid(groupWxid);
        record.setTargetUserWxid(targetUserWxid);
        record.setMsgFrom(msgFrom);
        PageHelper.startPage(pageNum,pageSize);
        return new PageBean<WcWxMsgEntity>(wcWxMsgMapper.select(record));
    }

    @Override
    public WcWxMsgVo entityToVo(WcMachineCodeEntity machineCodeEntity ,WcWxAccountEntity accountEntity,WcWxMsgEntity wcWxMsgEntity) {
        WcWxMsgVo wxMsgVo = new WcWxMsgVo();
        wxMsgVo.setMachineCode(machineCodeEntity.getMachineCode());
        wxMsgVo.setClientId(accountEntity.getTcpId());
        wxMsgVo.setAccountWxid(accountEntity.getWxid());
        wxMsgVo.setIsGroupMsg(wcWxMsgEntity.getChatRoom() == 1);
        wxMsgVo.setGroupWxid(wcWxMsgEntity.getChatRoomWxid());
        wxMsgVo.setTargetUserWxid(wcWxMsgEntity.getTargetUserWxid());
        wxMsgVo.setMsgType(wcWxMsgEntity.getType());
        wxMsgVo.setAtUserListWxids(praseAtWxid(wcWxMsgEntity.getMsgResource()));
        wxMsgVo.setMsgBody(Base64.encode(wcWxMsgEntity.getMsg()));
        return wxMsgVo;
    }

    /**
     * 解析@wxid
     * @param wxXmlMsgResource
     * @return
     */
    private String praseAtWxid(String wxXmlMsgResource){
        if(StrUtil.isBlank(wxXmlMsgResource))
            return null;
        Document document = XmlUtil.parseXml(wxXmlMsgResource);
        NodeList nodeList = document.getElementsByTagName("atuserlist");
        if(nodeList.getLength() <= 0)
            return null;
        List<String> atWxids = new ArrayList();

        for (int i = 0; i < nodeList.getLength(); i++) {
            atWxids.add(nodeList.item(i).getTextContent());
        }
        return atWxids.stream().collect(Collectors.joining(","));

    }
}
