package cn.edu.buaa.act.hybridcloud.gossip.service;

import cn.edu.buaa.act.hybridcloud.gossip.gms.SubmitDigest;
import cn.edu.buaa.act.hybridcloud.gossip.utils.InetSocketAddressUtil;
import cn.edu.buaa.act.hybridcloud.gossip.gms.LogsDigestSynMessage;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by toler on 2016/10/10.
 */
public class TestLogDigestSynMessage {

    public static void main(String args[]) throws IOException{
        List<SubmitDigest> list = new ArrayList<SubmitDigest>();
        for(int i = 0; i < 3; i++){
            Set<InetSocketAddress> tmp= new HashSet<InetSocketAddress>();
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2001"));
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2002"));
            tmp.add(InetSocketAddressUtil.parseInetSocketAddress("localhost:2003"));

            SubmitDigest sub = new SubmitDigest(i,i+1,"hehe",tmp);
            list.add(sub);
        }
        long maxExecute = 12;
        String memberId = "hehe";
        LogsDigestSynMessage logs1 = new LogsDigestSynMessage(memberId,list,maxExecute);
        DataOutput dos = new DataOutputStream(new FileOutputStream("tmp.dat"));
        LogsDigestSynMessage.serializer().serialize(logs1,dos);

        LogsDigestSynMessage logs2 = LogsDigestSynMessage.serializer().deserialize(new DataInputStream(new FileInputStream("tmp.dat")));

        System.out.println(logs2);


    }
}
