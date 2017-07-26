package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayInputStream;
import cn.edu.buaa.act.hybridcloud.gossip.net.IVerbHandler;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by toler on 2016/10/21.
 */
public class LogRequireTOSNHandler implements IVerbHandler {
    private static Logger logger = Logger.getLogger(LogRequireTOSNHandler.class);
    @Override
    public void doVerb(Message message, String id) {
        InetSocketAddress from = message.getFrom();
        if(logger.isTraceEnabled()){
            logger.trace("Received a LogRequireTTSMMessage from " + from);
        }
        if(!Gossiper.instance.isEnabled()){
            if(logger.isTraceEnabled()){
                logger.trace("Ignoring LogRequireTTSMMessage because is disabled");
            }
            return;
        }
        byte[] bytes = message.getMessageBody();
        DataInputStream dis = new DataInputStream(new FastByteArrayInputStream(bytes));
        try {
            //System.out.println("Received LogRequireMessage from "+ from +":--------------------------------------------------------");
            LogRequireTOSNMessage logRequireTOSNMessage = LogRequireTOSNMessage.serializer().deserialize(dis);
            LogProtos.Log log = logRequireTOSNMessage.getLog();
            if(log == null){
                if(logger.isTraceEnabled()){
                    logger.trace("The LogRequireTTSNMessage is null");
                }
                return;
            }
            if(!Gossiper.isMaster){
                if (logger.isTraceEnabled()){
                    logger.trace("I am not a master");
                }
                return;
            }
            List<SubmitDigest> submitDigests = new ArrayList<SubmitDigest>();
            Gossiper.instance.setTOSNAndApplyLocalSubmit(log, submitDigests);
            //System.out.println("SubmitDigests--------------------" + submitDigests);
            for(SubmitDigest submitDigest : submitDigests){
                if(submitDigest.getMemberId().equals(GossiperDescriptor.getMemberId())){
                    continue;
                }
                LogResponseTOSNMessage logResponseTOSNMessage = new LogResponseTOSNMessage(submitDigest);
                Message lResponseTOSNMessage = Gossiper.instance.makeLogResponseTOSNMessage(logResponseTOSNMessage);
                for(Map.Entry<InetSocketAddress, String> entry : Gossiper.instance.getMembersString().entrySet()){
                    if(entry.getValue().equals(submitDigest.getMemberId())){
                        from = entry.getKey();
                        break;
                    }
                }
                if(logger.isTraceEnabled()){
                    logger.trace("Sending a LogResponseMessage to " + from);
                }
                MessagingService.instance().sendOneWay(lResponseTOSNMessage, from);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
