package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayInputStream;
import cn.edu.buaa.act.hybridcloud.gossip.net.IVerbHandler;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by toler on 2016/10/16.
 */
public class LogsDigestAckHandler implements IVerbHandler{
    private static Logger logger_ = Logger.getLogger(LogsDigestAckHandler.class);

    @Override
    public void doVerb(Message message, String id) {
        InetSocketAddress from = message.getFrom();
        if(logger_.isTraceEnabled()){
            logger_.trace("Received a LogsDigestAckMessage from " + from);
        }
        if(!Gossiper.instance.isEnabled()){
            if (logger_.isTraceEnabled())
                logger_.trace("Ignoring LogsDigestAckMessage because gossip is disabled");
            return;
        }
        byte[] bytes = message.getMessageBody();
        DataInputStream dis = new DataInputStream( new FastByteArrayInputStream(bytes));
        try{
            LogsDigestAckMessage logsDigestAckMessage = LogsDigestAckMessage.serializer().deserialize(dis);
            List<SubmitDigest> submitDigests = logsDigestAckMessage.getSubmitDigests();
            List<LogProtos.Log> logs = logsDigestAckMessage.getLogs();
            List<RequestDigest> requestDigests = logsDigestAckMessage.getRequestDigests();
            //System.out.println("Received LogsDigestAckMessage from ("+ from +"):--------------------------------------------------------------------------");
            System.out.println(logsDigestAckMessage.toString());
            Gossiper.instance.applyLogsAckMessageLocally(submitDigests, logs);
            List<LogProtos.Log> sendLogs = new ArrayList<LogProtos.Log>(requestDigests.size());
            Gossiper.instance.getSendLogs(requestDigests, sendLogs);
            if(sendLogs.size() > 0){
                LogsDigestAck2Message logsDigestAck2Message = new LogsDigestAck2Message(sendLogs);
                Message lDigestAck2Message = Gossiper.instance.makeLogsDigestAck2Message(logsDigestAck2Message);
                if(logger_.isTraceEnabled())
                    logger_.trace("Sending a LogsDigestAck2Message to {}" +  from);
                //System.out.println("Sending a LogsDigestAck2Message to {}" +  from);
                MessagingService.instance().sendOneWay(lDigestAck2Message, from);
            }
            Gossiper.instance.checkSubmitLogsAndApply();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
