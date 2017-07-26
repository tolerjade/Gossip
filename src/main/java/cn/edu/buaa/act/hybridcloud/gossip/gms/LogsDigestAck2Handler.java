package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayInputStream;
import cn.edu.buaa.act.hybridcloud.gossip.net.IVerbHandler;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by toler on 2016/10/16.
 */
public class LogsDigestAck2Handler implements IVerbHandler{
    private static Logger logger_ = Logger.getLogger(LogsDigestAck2Handler.class);

    @Override
    public void doVerb(Message message, String id) {
        InetSocketAddress from = message.getFrom();
        if(logger_.isTraceEnabled()){
            logger_.trace("Received a LogsDigestAck2Message from " + from);
        }
        if(!Gossiper.instance.isEnabled()){
            if (logger_.isTraceEnabled())
                logger_.trace("Ignoring LogsDigestAck2Message because gossip is disabled");
            return;
        }
        byte[] bytes = message.getMessageBody();
        DataInputStream dis = new DataInputStream( new FastByteArrayInputStream(bytes));
        try{
            LogsDigestAck2Message logsDigestAck2Message = LogsDigestAck2Message.serializer().deserialize(dis);
            List<LogProtos.Log> logs = logsDigestAck2Message.getLogs();
            Gossiper.instance.applyLocalAck2MessageLocally(logs);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
