package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.proto.RequestProtos;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by toler on 2017/1/9.
 */
public class WriteServer extends Thread {
    private static final Logger logger = Logger.getLogger(WriteServer.class);
    private static final int OPEN_RETRY_DELAY = 100; // ms between retries

    // sending thread reads from "active" (one of queue1, queue2) until it is empty.
    // then it swaps it with "backlog."
    private volatile BlockingQueue<Entry> backlog = new LinkedBlockingQueue<Entry>();
    private volatile BlockingQueue<Entry> active = new LinkedBlockingQueue<Entry>();
    public static int count = 0;
    public void enqueue(LogProtos.Log message)
    {
        //System.out.println("enqueue---------------------------------------------------------" + count++);
        //expireMessages();
        try
        {
            backlog.put(new Entry(message,System.currentTimeMillis()));
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
    }
    private void expireMessages()
    {
        while (true)
        {
            Entry entry1 = backlog.peek();
            if (entry1 == null)
                break;

            Entry entry2 = backlog.poll();
            if (entry1 != entry2)
            {
                // sending thread switched queues.  add this entry (from the "new" backlog)
                // at the end of the active queue, which keeps it in the same position relative to the other entries
                // without having to contend with other clients for the head-of-backlog lock.
                if (entry2 != null)
                    active.add(entry2);
                break;
            }
        }
    }

    void shutdown(){
        backlog.clear();
        active.clear();
    }
    public void run()
    {
        int i = 0;
        while (true)
        {
            Entry entry = active.poll();
            if (entry == null)
            {
                // exhausted the active queue.  switch to backlog, once there's something to process there
                try
                {
                    entry = backlog.take();
                }
                catch (InterruptedException e)
                {
                    throw new AssertionError(e);
                }

                BlockingQueue<Entry> tmp = backlog;
                backlog = active;
                active = tmp;
            }

            LogProtos.Log m = entry.message;

            RequestProtos.Request request = m.getUpdate();
            synchronized (FileWrite.instatence){
                count ++;
                //System.out.println("-------------------------------------------------------------------------------------" + i++);
                FileWrite.instatence.writeToFile(GossiperDescriptor.getMountPoint() + File.separator + request.getFileName(), Long.parseLong(request.getId()),request.getValue().toByteArray());
            }

            //else
                // clear out the queue, else gossip messages back up.
              //  active.clear();
        }
    }
    private static class Entry
    {
        final LogProtos.Log message;
        final long timestamp;

        Entry(LogProtos.Log message, long timestamp)
        {
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
