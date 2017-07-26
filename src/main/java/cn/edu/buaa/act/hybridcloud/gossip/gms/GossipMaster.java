package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.utils.SynLinkedList;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by toler on 2016/10/24.
 */
public class GossipMaster {
    private static Logger logger = Logger.getLogger(GossipMaster.class);
    private static AtomicLong TOSN = new AtomicLong(0);
    private ConcurrentHashMap<String, MemberEntry> members = new ConcurrentHashMap();

    public long incrementTOSN() {
        return TOSN.incrementAndGet();
    }

    public long getTOSN() {
        return TOSN.get();
    }

    public ConcurrentHashMap<String, MemberEntry> getMembers() {
        return members;
    }

    public boolean putLog(String member, LogProtos.Log log) {
        if (!members.containsKey(member)) {
            members.put(member, new MemberEntry());
        }
        MemberEntry memberEntry = members.get(member);
        LogProtos.Log.Builder builder = log.toBuilder();
        builder.setDate(String.valueOf(System.currentTimeMillis()));
        memberEntry.linkedList.add(builder.build());
        return true;
    }

    public String check() {
        String turn = null;
        long minTime = Long.MAX_VALUE;
        for (Map.Entry<String, MemberEntry> entry : members.entrySet()) {
            MemberEntry memberEntry = entry.getValue();
            LogProtos.Log log = memberEntry.linkedList.getFirst();
            if (log != null) {
                System.out.println("-----------------------------------" + entry.getKey() + " : " + entry.getValue().linkedList.size() + " expected:" + memberEntry.expectTSM + " now:" + log.getTSM());
            } else {
                System.out.println("-----------------------------------" + entry.getKey() + " : " + entry.getValue().linkedList.size() + " expected:" + memberEntry.expectTSM);
            }
            if (log != null && log.getTSM() == memberEntry.expectTSM) {
                if (Long.parseLong(log.getDate()) < minTime) {
                    minTime = Long.parseLong(log.getDate());
                    turn = entry.getKey();
                }
            }
        }
        return turn;
    }

    public LogProtos.Log.Builder setLogTOSN(String member) {
        MemberEntry memberEntry = members.get(member);
        synchronized (memberEntry) {
            System.out.println("--------------------------------Remove:" + member);
            LogProtos.Log.Builder logBuilder = memberEntry.linkedList.removeFirst().toBuilder();
            logBuilder.setTOSN(getTOSN());
            incrementTOSN();
            memberEntry.expectTSM++;
            return logBuilder;
        }
    }

    class MemberEntry {
        long expectTSM = 0;
        SynLinkedList linkedList = new SynLinkedList();
    }

}
