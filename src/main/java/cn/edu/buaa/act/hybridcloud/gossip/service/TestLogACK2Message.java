package cn.edu.buaa.act.hybridcloud.gossip.service;

import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.proto.RequestProtos;
import cn.edu.buaa.act.hybridcloud.gossip.gms.LogsDigestAck2Message;
import com.google.protobuf.ByteString;

import java.io.*;
import java.util.*;

/**
 * Created by toler on 2016/10/12.
 */
public class TestLogACK2Message {
    public static void main(String args[])throws IOException{
        List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>();
        for(int i = 0; i < 3; i++){
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
            //logBuilder.getReceivedList().add("MyCluster " + i);
            logs.add(logBuilder.build());
        }
        LogsDigestAck2Message logsDigestAck2Message1 = new LogsDigestAck2Message(logs);
        DataOutput dos = new DataOutputStream(new FileOutputStream("tmp.dat"));
        LogsDigestAck2Message.serializer().serialize(logsDigestAck2Message1,dos);
        LogsDigestAck2Message logsDigestAck2Message2 = LogsDigestAck2Message.serializer().deserialize(new DataInputStream(new FileInputStream("tmp.dat")));
        System.out.println(logsDigestAck2Message2);
    }
}
