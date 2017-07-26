package cn.edu.buaa.act.hybridcloud.gossip.service;

import cn.edu.buaa.act.hybridcloud.gossip.gms.LogsDigestAckMessage;
import cn.edu.buaa.act.hybridcloud.gossip.gms.SubmitDigest;
import cn.edu.buaa.act.hybridcloud.gossip.utils.InetSocketAddressUtil;
import cn.edu.buaa.act.hybridcloud.gossip.gms.RequestDigest;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.proto.RequestProtos;
import com.google.protobuf.ByteString;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by toler on 2016/10/12.
 */
public class TestLogACKMessage {
    public static void main(String args[])throws IOException{
        List<RequestDigest> requestDigests = new ArrayList<RequestDigest>();
        List<SubmitDigest> submitDigests = new ArrayList<SubmitDigest>();
        List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>();
        for(int i = 0; i < 3; i++){
            RequestDigest  requestDigest = new RequestDigest("request" + i, i + 100);
            Set<InetSocketAddress> tmp= new HashSet<InetSocketAddress>();
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2001"));
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2002"));
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2003"));

            SubmitDigest sub = new SubmitDigest(i,i+1,"hehe",tmp);
            RequestProtos.Request.Builder requestBuilder = RequestProtos.Request.newBuilder();
            requestBuilder.setFileName("/tmp/heh");
            requestBuilder.setId("1234");
            requestBuilder.setValue(ByteString.copyFrom("caonima".getBytes()));
            LogProtos.Log.Builder logBuilder = LogProtos.Log.newBuilder();
            logBuilder.setDate(new Date().toString());
            logBuilder.setMemberID("MyCluster "+ i);
            logBuilder.setTOSN(123);
            logBuilder.setTSM(445);
            logBuilder.addReceived("MyCluster" + i);
            logBuilder.setUpdate(requestBuilder.build());
            logBuilder.getReceivedList().add("MyCluster " + i);
            //requestDigests.add(requestDigest);
            submitDigests.add(sub);
            LogProtos.Log log = logBuilder.build();
            logs.add(log);
        }
        LogsDigestAckMessage logsDigestAckMessage1 = new LogsDigestAckMessage(requestDigests, submitDigests, logs);
        DataOutput dos = new DataOutputStream(new FileOutputStream("tmp.dat"));
        LogsDigestAckMessage.serializer().serialize(logsDigestAckMessage1,dos);
        LogsDigestAckMessage logsDigestAckMessage2 = LogsDigestAckMessage.serializer().deserialize(new DataInputStream(new FileInputStream("tmp.dat")));
        System.out.println(logsDigestAckMessage2);
    }
}
