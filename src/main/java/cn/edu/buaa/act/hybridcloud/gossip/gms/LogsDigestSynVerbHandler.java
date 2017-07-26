package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayInputStream;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.net.IVerbHandler;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by toler on 2016/10/11.
 */
public class LogsDigestSynVerbHandler implements IVerbHandler{
    private static Logger logger_ = Logger.getLogger(LogsDigestSynVerbHandler.class);

    @Override
    public void doVerb(Message message, String id) {
        InetSocketAddress from = message.getFrom();
        if(logger_.isTraceEnabled()){
            logger_.trace("Received a LogsDigestSynMessage from " + from);
        }
        if(!Gossiper.instance.isEnabled()){
            if (logger_.isTraceEnabled())
                logger_.trace("Ignoring LogsDigestSynMessage because gossip is disabled");
            return;
        }
        byte[] bytes = message.getMessageBody();
        DataInputStream dis = new DataInputStream( new FastByteArrayInputStream(bytes));
        try{
            LogsDigestSynMessage  logsDigestSynMessage = LogsDigestSynMessage.serializer().deserialize(dis);
            if(!logsDigestSynMessage.memberId_.equals(GossiperDescriptor.getMemberId())){
                //logger_.warn("Cluster MemberId mismatch form " + from + " " + logsDigestSynMessage.memberId_ + "!=" + GossiperDescriptor.getMemberId());
                Gossiper.instance.updateMemberString(logsDigestSynMessage.memberId_, from);
            }

            List<SubmitDigest> submitDigestLists = logsDigestSynMessage.getSubmitDigests();
            if (logger_.isTraceEnabled() ||logger_.isInfoEnabled())
            {
                StringBuilder sb = new StringBuilder();
                for ( SubmitDigest sDigest : submitDigestLists )
                {
                    sb.append(sDigest);
                    sb.append(" ");
                }
                logger_.trace("Gossip submit digests are : " + sb.toString());
            }

            Long remoteMaxExecute = logsDigestSynMessage.getMaxExecuteRequest();
            //System.out.println("Received LogsDigestSynMessage from "+ from +":--------------------------------------------------------");
            //System.out.println(logsDigestSynMessage.toString());
            List<LogProtos.Log> sendLogs = new ArrayList<LogProtos.Log>();
            List<SubmitDigest>  sendAcceptedMembers = new ArrayList<SubmitDigest>();
            List<RequestDigest> expectedRequests = new ArrayList<RequestDigest>();
            doSort(submitDigestLists);
            Gossiper.instance.examineLogsDigest(expectedRequests, submitDigestLists, sendAcceptedMembers, remoteMaxExecute, sendLogs);
            //System.out.println("Request--------------------" + expectedRequests);
            //System.out.println("SendSubmit--------------------" + sendAcceptedMembers);
            //System.out.println("SendLogs--------------------" + sendLogs.size());
            if(expectedRequests.size() > 0 || sendAcceptedMembers.size() > 0 || sendLogs.size() >0) {
                LogsDigestAckMessage logsDigestAckMessage = new LogsDigestAckMessage(expectedRequests, sendAcceptedMembers, sendLogs);
                Message gLogsAckMessage = Gossiper.instance.makeLogsDigestAckMessage(logsDigestAckMessage);
                if (logger_.isTraceEnabled()) {
                    logger_.trace("Sending a LogsDigestAckMessage to " + from);
                }
                MessagingService.instance().sendOneWay(gLogsAckMessage, from);
            }
            Gossiper.instance.checkSubmitLogsAndApply();
            //缺少Failure监测
            // Gossiper.instance.notifyFailureDetector(receivedDigest);



        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private void doSort(List<SubmitDigest> lists){
        Collections.sort(lists, new Comparator<SubmitDigest>() {
            @Override
            public int compare(SubmitDigest o1, SubmitDigest o2) {
                return (int) (o1.getTOSN() - o2.getTOSN());
            }
        });
    }
}
