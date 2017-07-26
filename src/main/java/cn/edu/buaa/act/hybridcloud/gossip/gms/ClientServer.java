package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.proto.RequestProtos;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by toler on 2016/12/6.
 */
public class ClientServer extends Thread {
    public static final ClientServer instance = new ClientServer();
    private static Logger logger = Logger.getLogger(ClientServer.class);

    static {
        instance.start();
    }

    private AtomicLong nextTSM = new AtomicLong(0);
    private volatile BlockingQueue<LogProtos.Log> backlog = new LinkedBlockingQueue();
    private volatile BlockingQueue<LogProtos.Log> active = new LinkedBlockingQueue();

    public long getNextTSM() {
        return nextTSM.get();
    }

    public void enqueue(LogProtos.Log message) {
        //System.out.println("enqueue---------------------------------------------------------" + count++);
        //expireMessages();
        try {
            backlog.put(message);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    public synchronized void addLogToReceived(String fileName, long offset, byte[] vlaue) throws IOException {
        RequestProtos.Request.Builder builder = RequestProtos.Request.newBuilder();
        builder.setValue(ByteString.copyFrom(vlaue));
        builder.setId(String.valueOf(offset));
        builder.setFileName(fileName);
        LogProtos.Log.Builder logBuilder = LogProtos.Log.newBuilder();
        logBuilder.setMemberID(GossiperDescriptor.getMemberId());
        logBuilder.addReceived(GossiperDescriptor.getMemberId());
        logBuilder.setUpdate(builder.build());
        logBuilder.setTSM(nextTSM.getAndIncrement());
        logBuilder.setTOSN(-1);
        logBuilder.setDate(String.valueOf(System.currentTimeMillis()));//接受消息时的时间
        try {
            backlog.put(logBuilder.build());
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        /*
        LogRequireTOSNMessage message = new LogRequireTOSNMessage(logBuilder.build());
        Message received = Gossiper.instance.makeLogRequireTOSNMessage(message);
        while (Gossiper.instance.getReceivedRequests().size() > 1000){
            try {
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Gossiper.instance.getReceivedRequests().add(logBuilder.build());
        MessagingService.instance().sendOneWay(received, GossiperDescriptor.getMaster());
        */
    }

    public void run() {
        int i = 0;
        while (true) {
            LogProtos.Log entry = active.poll();
            if (entry == null) {
                // exhausted the active queue.  switch to backlog, once there's something to process there
                try {
                    entry = backlog.take();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }

                BlockingQueue<LogProtos.Log> tmp = backlog;
                backlog = active;
                active = tmp;
            }
            LogRequireTOSNMessage message = new LogRequireTOSNMessage(entry);
            Message received = null;
            try {
                received = Gossiper.instance.makeLogRequireTOSNMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (Gossiper.instance.getReceivedRequests().size() > 1000) {
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Gossiper.instance.getReceivedRequests().add(entry);
            MessagingService.instance().sendOneWay(received, GossiperDescriptor.getMaster());
            //System.out.println("-------------------------------------------------------------------------------------" + i++);


            //else
            // clear out the queue, else gossip messages back up.
            //  active.clear();
        }
    }
}
