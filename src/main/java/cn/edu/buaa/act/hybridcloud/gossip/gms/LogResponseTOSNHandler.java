package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayInputStream;
import cn.edu.buaa.act.hybridcloud.gossip.net.IVerbHandler;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by toler on 2016/10/21.
 */
public class LogResponseTOSNHandler implements IVerbHandler{
    private static Logger logger = Logger.getLogger(LogResponseTOSNHandler.class);
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
            //System.out.println("Received LogResponseMessage from "+ from +":--------------------------------------------------------");
            LogResponseTOSNMessage logResponseTTSNMessage = LogResponseTOSNMessage.serializer().deserialize(dis);
            SubmitDigest submitDigest = logResponseTTSNMessage.getSubmitDigest();
            //System.out.println("Response:" + submitDigest);
            Gossiper.instance.applyLogResponseTOSNMessage(submitDigest);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
