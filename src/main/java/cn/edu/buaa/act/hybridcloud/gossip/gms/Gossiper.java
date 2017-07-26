/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.edu.buaa.act.hybridcloud.gossip.gms;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import cn.edu.buaa.act.hybridcloud.gossip.concurrent.DebuggableScheduledThreadPoolExecutor;
import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.io.util.FastByteArrayOutputStream;
import cn.edu.buaa.act.hybridcloud.gossip.locator.IApplicationStateStarting;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessageProducer;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessageVerb;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.utils.FBUtilities;
import cn.edu.buaa.act.hybridcloud.gossip.utils.InetSocketAddressUtil;
import cn.edu.buaa.act.hybridcloud.gossip.utils.SynLinkedList;
import org.apache.log4j.Logger;

/**
 * This module is responsible for Gossiping information for the local endpoint. This abstraction
 * maintains the list of live and dead endpoints. Periodically i.e. every 1 second this module
 * chooses a random node and initiates a round of Gossip with it. A round of Gossip involves 3
 * rounds of messaging. For instance if node A wants to initiate a round of Gossip with node B
 * it starts off by sending node B a GossipDigestSynMessage. Node B on receipt of this message
 * sends node A a GossipDigestAckMessage. On receipt of this message node A sends node B a
 * GossipDigestAck2Message which completes a round of Gossip. This module as and when it hears one
 * of the three above mentioned messages updates the Failure Detector with the liveness information.
 * Upon hearing a GossipShutdownMessage, this module will instantly mark the remote node as down in
 * the Failure Detector.
 */

public class Gossiper implements IFailureDetectionEventListener, GossiperMBean
{
    /*
    实验
     */
    public Map<Long,Long> count = new TreeMap<Long, Long>();
    private static final String MBEAN_NAME = "com.netease.active.appengine.gossip.gms:type=Gossiper";

    private static final DebuggableScheduledThreadPoolExecutor executor = new DebuggableScheduledThreadPoolExecutor("GossipTasks");

    static final ApplicationState[] STATES = ApplicationState.values();
    static final List<String> DEAD_STATES = Arrays.asList(VersionedValue.REMOVING_TOKEN, VersionedValue.REMOVED_TOKEN);

    private ScheduledFuture<?> scheduledGossipTask;
    public final static int intervalInMillis = 1000;
    public final static int QUARANTINE_DELAY = GossiperDescriptor.getRing_delay() * 2;
    private static Logger logger = Logger.getLogger(Gossiper.class);
    public static final Gossiper instance = new Gossiper();
    private InetSocketAddress masterAddress;
    private static GossipMaster gMaster;
    private static WriteServer writeServer;
    public static boolean isMaster = false;
    private long FatClientTimeout;
    private Random random = new Random();
    private Comparator<InetSocketAddress> inetcomparator = new Comparator<InetSocketAddress>()
    {
        public int compare(InetSocketAddress addr1,  InetSocketAddress addr2)
        {
        	int i = addr1.getAddress().getHostAddress().compareTo(addr2.getAddress().getHostAddress());
        	if(i==0){
        		return (addr1.getPort()-addr2.getPort());
        	}
        	else{
        		return i;
        	}
        }
    };

    /* subscribers for interest in EndpointState change */
    private List<IEndpointStateChangeSubscriber> subscribers = new CopyOnWriteArrayList<IEndpointStateChangeSubscriber>();

    private List<IApplicationStateStarting> applicationstatestartings = new CopyOnWriteArrayList<IApplicationStateStarting>();
    
    /* live member set */
    private Set<InetSocketAddress> liveEndpoints = new ConcurrentSkipListSet<InetSocketAddress>(inetcomparator);

    /* unreachable member set */
    private Map<InetSocketAddress, Long> unreachableEndpoints = new ConcurrentHashMap<InetSocketAddress, Long>();

    /* initial seeds for joining the cluster */
    private Set<InetSocketAddress> seeds = new ConcurrentSkipListSet<InetSocketAddress>(inetcomparator);

    /* map where key is the endpoint and value is the state associated with the endpoint */
    Map<InetSocketAddress, EndpointState> endpointStateMap = new ConcurrentHashMap<InetSocketAddress, EndpointState>();

    /* map where key is endpoint and value is timestamp when this endpoint was removed from
     * gossip. We will ignore any gossip regarding these endpoints for QUARANTINE_DELAY time
     * after removal to prevent nodes from falsely reincarnating during the time when removal
     * gossip gets propagated to all nodes */
    private Map<InetSocketAddress, Long> justRemovedEndpoints = new ConcurrentHashMap<InetSocketAddress, Long>();

    /* add for updates*/
    private SynLinkedList receivedRequests = new SynLinkedList();

    private SynLinkedList submitRequests = new SynLinkedList();

    private SynLinkedList executeRequests = new SynLinkedList();

    private Map<InetSocketAddress, String> membersString= new HashMap();

    private AtomicLong currentTOSN = new AtomicLong(0);

    private AtomicBoolean isUsed = new AtomicBoolean(false);

    public SynLinkedList getReceivedRequests(){
        return receivedRequests;
    }

    public SynLinkedList getSubmitRequests() {
        return submitRequests;
    }

    public SynLinkedList getExecuteRequests() {
        return executeRequests;
    }

    private class GossipTask implements Runnable
    {
        public void run()
        {
            try
            {
                //wait on messaging service to start listening
                MessagingService.instance().waitUntilListening();

                /* Update the local heartbeat counter. */
                endpointStateMap.get(FBUtilities.getBroadcastAddress()).getHeartBeatState().updateHeartBeat();
                if (logger.isTraceEnabled())
                    logger.trace("My heartbeat is now " + endpointStateMap.get(FBUtilities.getBroadcastAddress()).getHeartBeatState().getHeartBeatVersion());
                final List<GossipDigest> gDigests = new ArrayList<GossipDigest>();
                Gossiper.instance.makeRandomGossipDigest(gDigests);

                if ( gDigests.size() > 0 )
                {
                    MessageProducer prod = new MessageProducer()
                    {
                        public Message getMessage() throws IOException
                        {
                            return makeGossipDigestSynMessage(gDigests);
                        }
                    };
                    /* Gossip to some random live member */
                    boolean gossipedToSeed = doGossipToLiveMember(prod);

                    /* Gossip to some unreachable member with some probability to check if he is back up */
                    doGossipToUnreachableMember(prod);

                    /* Gossip to a seed if we did not do so above, or we have seen less nodes
                       than there are seeds.  This prevents partitions where each group of nodes
                       is only gossiping to a subset of the seeds.

                       The most straightforward check would be to check that all the seeds have been
                       verified either as live or unreachable.  To avoid that computation each round,
                       we reason that:

                       either all the live nodes are seeds, in which case non-seeds that come online
                       will introduce themselves to a member of the ring by definition,

                       or there is at least one non-seed node in the list, in which case eventually
                       someone will gossip to it, and then do a gossip to a random seed from the
                       gossipedToSeed check.

                       See CASSANDRA-150 for more exposition. */
                    if (!gossipedToSeed || liveEndpoints.size() < seeds.size())
                        doGossipToSeed(prod);

                    if (logger.isTraceEnabled())
                        logger.trace("Performing status check ...");
                    doStatusCheck();
                }
                if(submitRequests.size() >= 0){
                    final List<SubmitDigest> submitDigests = new ArrayList(submitRequests.size());
                    final long maxExecute = executeRequests.size() > 0 ? executeRequests.getLast().getTOSN():-1;
                    synchronized (submitRequests){
                        Iterator<LogProtos.Log> it = submitRequests.iterator();
                        while (it.hasNext()) {
                            LogProtos.Log log = it.next();
                            Set<InetSocketAddress> set = new HashSet<InetSocketAddress>();
                            for (String member : log.getReceivedList()) {
                                for (Entry<InetSocketAddress, String> entry : membersString.entrySet()) {
                                    if (entry.getValue().equals(member)) {
                                        set.add(entry.getKey());
                                    }
                                }
                            }
                            SubmitDigest submitDigest = new SubmitDigest(log.getTOSN(), log.getTSM(), log.getMemberID(), set);
                            submitDigests.add(submitDigest);
                        }
                    }
                    MessageProducer prod = new MessageProducer()
                    {
                        public Message getMessage() throws IOException
                        {
                            return makeLogsDigestSynMessage(new LogsDigestSynMessage(GossiperDescriptor.getMemberId(),submitDigests, maxExecute));
                        }
                    };

                    boolean gossipToSeed = doGossipToLiveMember(prod);
                    //if(!gossipToSeed || liveEndpoints.size() < seeds.size()){
                    //    doGossipToSeed(prod);
                    //}

                    if(logger.isTraceEnabled()){
                        logger.trace("Send Log syn message ...");
                    }
                }
            }
            catch (Exception e)
            {
                logger.error("Gossip error", e);
            }
        }
    }

    private Gossiper()
    {
        // half of QUARATINE_DELAY, to ensure justRemovedEndpoints has enough leeway to prevent re-gossip
        FatClientTimeout = (long)(QUARANTINE_DELAY / 2);
        /* register with the Failure Detector for receiving Failure detector events */
        FailureDetector.instance.registerFailureDetectionEventListener(this);

        // Register this instance with JMX
        try
        {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.registerMBean(this, new ObjectName(MBEAN_NAME));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public Map<InetSocketAddress, String> getMembersString() {
        return membersString;
    }

    public static GossipMaster getgMaster() {
        return gMaster;
    }

    /**
     * Register for interesting state changes.
     * @param subscriber module which implements the IEndpointStateChangeSubscriber
     */
    public void register(IEndpointStateChangeSubscriber subscriber)
    {
        subscribers.add(subscriber);
    }
    
    /**
     * Register for interesting state changes.
     * @param applicationstatestarting subscriber module which implements the IEndpointStateChangeSubscriber
     */
    public void register(IApplicationStateStarting applicationstatestarting)
    {
    	applicationstatestartings.add(applicationstatestarting);
    }

    /**
     * Unregister interest for state changes.
     * @param subscriber module which implements the IEndpointStateChangeSubscriber
     */
    public void unregister(IEndpointStateChangeSubscriber subscriber)
    {
        subscribers.remove(subscriber);
    }

    public long getCurrentTOSN() {
        return currentTOSN.get();
    }

    public Set<InetSocketAddress> getLiveMembers()
    {
        Set<InetSocketAddress> liveMbrs = new HashSet<InetSocketAddress>(liveEndpoints);
        if (!liveMbrs.contains(FBUtilities.getBroadcastAddress()))
            liveMbrs.add(FBUtilities.getBroadcastAddress());
        return liveMbrs;
    }

    public Set<InetSocketAddress> getUnreachableMembers()
    {
        return unreachableEndpoints.keySet();
    }


    
    
    
    
    
    /**
     * Return either: the greatest heartbeat or application state
     * @param epState
     * @return
     */
    int getMaxEndpointStateVersion(EndpointState epState)
    {
        int maxVersion = epState.getHeartBeatState().getHeartBeatVersion();
        for (VersionedValue value : epState.getApplicationStateMapValues())
            maxVersion = Math.max(maxVersion,  value.version);
        return maxVersion;
    }

    /**
     * This method is part of IFailureDetectionEventListener interface. This is invoked
     * by the Failure Detector when it convicts an end point.
     * 由ApplicationState.STATUS来判断节点是否死掉，死掉则把isAlive标记为false。（主动remove一个节点的时候）
     * 
     * @param endpoint end point that is convicted.
    */
    public void convict(InetSocketAddress endpoint, double phi)
    {
        EndpointState epState = endpointStateMap.get(endpoint);
        if (epState.isAlive() && !isDeadState(epState))
        {
            markDead(endpoint, epState);
        }
    }

    /**
     * Removes the endpoint from gossip completely  
     * 删除endpointStateMap操作，但放入justRemovedEndpoints，不处理liveEndpoints（removeEndpoint处理）
     *
     * @param endpoint endpoint to be removed from the current membership.
    */
    private void evictFromMembership(InetSocketAddress endpoint)
    {
        unreachableEndpoints.remove(endpoint);
        endpointStateMap.remove(endpoint);
        quarantineEndpoint(endpoint);
        if (logger.isDebugEnabled())
            logger.debug("evicting " + endpoint + " from gossip");
    }

    /**
     * Removes the endpoint from Gossip but retains endpoint state
     * 删除liveEndpoints动作，放入justRemovedEndpoints，但保留endpointStateMap中的对象（等过期再删，evictFromMembership方法处理）
     * 注意触发了FailureDetector里arrivalSamples_的remove动作
     */
    public void removeEndpoint(InetSocketAddress endpoint)
    {
        // do subscribers first so anything in the subscriber that depends on gossiper state won't get confused
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
            subscriber.onRemove(endpoint);

        liveEndpoints.remove(endpoint);
        unreachableEndpoints.remove(endpoint);
        // do not remove endpointState until the quarantine expires
        FailureDetector.instance.remove(endpoint);
        quarantineEndpoint(endpoint);
        if (logger.isDebugEnabled())
            logger.debug("removing endpoint " + endpoint);
    }

    /**
     * Quarantines the endpoint for QUARANTINE_DELAY
     * @param endpoint
     */
    private void quarantineEndpoint(InetSocketAddress endpoint)
    {
        justRemovedEndpoints.put(endpoint, System.currentTimeMillis());
    }

    /**
     * The gossip digest is built based on randomization
     * rather than just looping through the collection of live endpoints.
     *
     * @param gDigests list of Gossip Digests.
    */
    private void makeRandomGossipDigest(List<GossipDigest> gDigests)
    {
        EndpointState epState;
        int generation = 0;
        int maxVersion = 0;

        // local epstate will be part of endpointStateMap   
        //系统启动后endpointStateMap一定不为空，本方法访问时，它至少有一个元素：当前节点
        List<InetSocketAddress> endpoints = new ArrayList<InetSocketAddress>(endpointStateMap.keySet());
        Collections.shuffle(endpoints, random);
        for (InetSocketAddress endpoint : endpoints)
        {
            epState = endpointStateMap.get(endpoint);
            if (epState != null)
            {
                generation = epState.getHeartBeatState().getGeneration();
                maxVersion = getMaxEndpointStateVersion(epState);
            }
            gDigests.add(new GossipDigest(endpoint, generation, maxVersion));
        }

        if (logger.isTraceEnabled())
        {
            StringBuilder sb = new StringBuilder();
            for ( GossipDigest gDigest : gDigests )
            {
                sb.append(gDigest);
                sb.append(" ");
            }
                logger.trace("Gossip Digests are : " + sb.toString());
        }
    }







/***
 * 以下创建各类Message（同步、响应、响应2、关闭）    
 */
    public Message makeLogRequireTOSNMessage(LogRequireTOSNMessage logRequireTOSNMessage)throws IOException{
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        LogRequireTOSNMessage.serializer().serialize(logRequireTOSNMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.LOG_REQUIRE_TOSN, bos.toByteArray());
    }

    Message makeLogResponseTOSNMessage(LogResponseTOSNMessage logResponseTOSNMessage)throws IOException{
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        LogResponseTOSNMessage.serializer().serialize(logResponseTOSNMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.LOG_RESPONSE_TOSN, bos.toByteArray());
    }

    Message makeLogsDigestAck2Message(LogsDigestAck2Message logsDigestAck2Message)throws IOException{
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        LogsDigestAck2Message.serializer().serialize(logsDigestAck2Message, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.LOGS_DIGEST_ACK2, bos.toByteArray());
    }

    Message makeLogsDigestAckMessage(LogsDigestAckMessage logsDigestAckMessage)throws IOException{
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        LogsDigestAckMessage.serializer().serialize(logsDigestAckMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.LOGS_DIGEST_ACK, bos.toByteArray());
    }

    Message makeLogsDigestSynMessage(LogsDigestSynMessage logsDigestSynMessage) throws IOException{
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        LogsDigestSynMessage.serializer().serialize(logsDigestSynMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.LOGS_DIGEST_SYN, bos.toByteArray());
    }

    Message makeGossipDigestSynMessage(List<GossipDigest> gDigests) throws IOException
    {
        GossipDigestSynMessage gDigestMessage = new GossipDigestSynMessage(GossiperDescriptor.getClusterName(), gDigests);
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream( bos );
        GossipDigestSynMessage.serializer().serialize(gDigestMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.GOSSIP_DIGEST_SYN, bos.toByteArray());
    }

    Message makeGossipDigestAckMessage(GossipDigestAckMessage gDigestAckMessage) throws IOException
    {
    	FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        GossipDigestAckMessage.serializer().serialize(gDigestAckMessage, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.GOSSIP_DIGEST_ACK, bos.toByteArray());
    }

    Message makeGossipDigestAck2Message(GossipDigestAck2Message gDigestAck2Message) throws IOException
    {
    	FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        GossipDigestAck2Message.serializer().serialize(gDigestAck2Message, dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.GOSSIP_DIGEST_ACK2, bos.toByteArray());
    }
    
    Message makeGossipShutdownMessage() throws IOException
    {
        FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        GossipShutdownMessage.serializer().serialize(new GossipShutdownMessage(), dos);
        return new Message(FBUtilities.getBroadcastAddress(), MessageVerb.Verb.GOSSIP_SHUTDOWN, bos.toByteArray());
    }

    
    
    
    
/***
 * 发送消息到各类节点：    
 */
    
    /**
     * Returns true if the chosen target was also a seed. False otherwise
     *
     *  @param prod produces a message to send
     *  @param epSet a set of endpoint from which a random endpoint is chosen.
     *  @return true if the chosen endpoint is also a seed.
     */
    private boolean sendGossip(MessageProducer prod, Set<InetSocketAddress> epSet)
    {
        int size = epSet.size();
        if (size < 1)
            return false;
        /* Generate a random number from 0 -> size */
        List<InetSocketAddress> liveEndpoints = new ArrayList<InetSocketAddress>(epSet);
        int index = (size == 1) ? 0 : random.nextInt(size);
        InetSocketAddress to = liveEndpoints.get(index);
        if (logger.isTraceEnabled())
            logger.trace("Sending a GossipDigestSynMessage to {} ..."+ to);
        try
        {
            MessagingService.instance().sendOneWay(prod.getMessage(), to);
        }
        catch (IOException ex)
        {
            throw new IOError(ex);
        }
        return seeds.contains(to);
    }

    /* Sends a Gossip message to a live member and returns true if the recipient was a seed */
    private boolean doGossipToLiveMember(MessageProducer prod)
    {
        int size = liveEndpoints.size();
        if ( size == 0 )
            return false;
        return sendGossip(prod, liveEndpoints);
    }

    /* Sends a Gossip message to an unreachable member */
    private void doGossipToUnreachableMember(MessageProducer prod)
    {
        double liveEndpointCount = liveEndpoints.size();
        double unreachableEndpointCount = unreachableEndpoints.size();
        if ( unreachableEndpointCount > 0 )
        {
            /* based on some probability */
            double prob = unreachableEndpointCount / (liveEndpointCount + 1);
            double randDbl = random.nextDouble();
            if ( randDbl < prob )
                sendGossip(prod, unreachableEndpoints.keySet());
        }
    }

    /* Gossip to a seed for facilitating partition healing */
    private void doGossipToSeed(MessageProducer prod)
    {
        int size = seeds.size();
        if ( size > 0 )
        {
            if ( size == 1 && seeds.contains(FBUtilities.getBroadcastAddress()) )
            {
                return;
            }

            if ( liveEndpoints.size() == 0 )
            {
                sendGossip(prod, seeds);
            }
            else
            {
                /* Gossip with the seed with some probability. */
                double probability = seeds.size() / (double)( liveEndpoints.size() + unreachableEndpoints.size() );
                double randDbl = random.nextDouble();
                if ( randDbl <= probability )
                    sendGossip(prod, seeds);
            }
        }
    }

    
    
    /***
     * 每秒定时检查各节点状态
     */
    private void doStatusCheck()
    {
        long now = System.currentTimeMillis();

        Set<InetSocketAddress> eps = endpointStateMap.keySet();
        for ( InetSocketAddress endpoint : eps )
        {
            if ( endpoint.equals(FBUtilities.getBroadcastAddress()) )
                continue;

            FailureDetector.instance.interpret(endpoint);
            EndpointState epState = endpointStateMap.get(endpoint);
            if ( epState != null )
            {
                long duration = now - epState.getUpdateTimestamp();

                // check if this is a fat client. fat clients are removed automatically from
                // gossip after FatClientTimeout.  Do not remove dead states here.
                if (!isDeadState(epState) && !epState.isAlive() && !justRemovedEndpoints.containsKey(endpoint) && (duration > FatClientTimeout))
                {
                    logger.info("FatClient " + endpoint + " has been silent for " + FatClientTimeout + "ms, removing from gossip");
                    removeEndpoint(endpoint); // will put it in justRemovedEndpoints to respect quarantine delay
                    evictFromMembership(endpoint); // can get rid of the state immediately
                }

            }
        }

        if (!justRemovedEndpoints.isEmpty())
        {
            for (Entry<InetSocketAddress, Long> entry : justRemovedEndpoints.entrySet())
            {
                if ((now - entry.getValue()) > QUARANTINE_DELAY)
                {
                    if (logger.isDebugEnabled())
                        logger.debug(QUARANTINE_DELAY + " elapsed, " + entry.getKey() + " gossip quarantine over");
                    justRemovedEndpoints.remove(entry.getKey());
                }
            }
        }
    }


    
    
/***
 * 提供外部类访问节点状态、节点列表的方法    
 */
    
    public EndpointState getEndpointStateForEndpoint(InetSocketAddress ep)
    {
        return endpointStateMap.get(ep);
    }

    public Set<Entry<InetSocketAddress, EndpointState>> getEndpointStates()
    {
        return endpointStateMap.entrySet();
    }

    

/***
 * 三次消息通讯相关处理    
 */
    
 //创建消息时提供版本较新的EndpointState    
    EndpointState getStateForVersionBiggerThan(InetSocketAddress forEndpoint, int version)
    {
        EndpointState epState = endpointStateMap.get(forEndpoint);
        EndpointState reqdEndpointState = null;

        if ( epState != null )
        {
            /*
             * Here we try to include the Heart Beat state only if it is
             * greater than the version passed in. It might happen that
             * the heart beat version maybe lesser than the version passed
             * in and some application state has a version that is greater
             * than the version passed in. In this case we also send the old
             * heart beat and throw it away on the receiver if it is redundant.
            */
            int localHbVersion = epState.getHeartBeatState().getHeartBeatVersion();
            if ( localHbVersion > version )
            {
                reqdEndpointState = new EndpointState(epState.getHeartBeatState());
                if (logger.isTraceEnabled())
                    logger.trace("local heartbeat version " + localHbVersion + " greater than " + version + " for " + forEndpoint);
            }
            /* Accumulate all application states whose versions are greater than "version" variable */
            for (Entry<ApplicationState, VersionedValue> entry : epState.getApplicationStateMapEntrySet())
            {
                VersionedValue value = entry.getValue();
                if ( value.version > version )
                {
                    if ( reqdEndpointState == null )
                    {
                        reqdEndpointState = new EndpointState(epState.getHeartBeatState());
                    }
                    final ApplicationState key = entry.getKey();
                    if (logger.isTraceEnabled())
                        logger.trace("Adding state " + key + ": " + value.value);
                    reqdEndpointState.addApplicationState(key, value);
                }
            }
        }
        return reqdEndpointState;
    }


    void notifyFailureDetector(List<GossipDigest> gDigests)
    {
        for ( GossipDigest gDigest : gDigests )
        {
            notifyFailureDetector(gDigest.endpoint, endpointStateMap.get(gDigest.endpoint));
        }
    }

    void notifyFailureDetector(Map<InetSocketAddress, EndpointState> remoteEpStateMap)
    {
        for (Entry<InetSocketAddress, EndpointState> entry : remoteEpStateMap.entrySet())
        {
            notifyFailureDetector(entry.getKey(), entry.getValue());
        }
    }

    //先比较Generation，更新时间，并不替换本地的EndpointState里的其它信息
    //注意和故障检测有关，涉及FailureDetector类中的arrivalSamples_
    void notifyFailureDetector(InetSocketAddress endpoint, EndpointState remoteEndpointState)
    {
        EndpointState localEndpointState = endpointStateMap.get(endpoint);
        /*
         * If the local endpoint state exists then report to the FD only
         * if the versions workout.
        */
        if ( localEndpointState != null )
        {
            IFailureDetector fd = FailureDetector.instance;
            int localGeneration = localEndpointState.getHeartBeatState().getGeneration();
            int remoteGeneration = remoteEndpointState.getHeartBeatState().getGeneration();
            if ( remoteGeneration > localGeneration )
            {
                localEndpointState.updateTimestamp();
                // this node was dead and the generation changed, this indicates a reboot, or possibly a takeover
                // we will clean the fd intervals for it and relearn them
                if (!localEndpointState.isAlive())
                {
                    logger.debug("Clearing interval times for {} due to generation change"+ endpoint);
                    fd.clear(endpoint);
                }
                fd.report(endpoint);
                return;
            }

            if ( remoteGeneration == localGeneration )
            {
                int localVersion = getMaxEndpointStateVersion(localEndpointState);
                int remoteVersion = remoteEndpointState.getHeartBeatState().getHeartBeatVersion();
                if ( remoteVersion > localVersion )
                {
                    localEndpointState.updateTimestamp();
                    // just a version change, report to the fd
                    fd.report(endpoint);
                }
            }
        }

    }

    
    
    

    
/***
 *  共三次收发消息时，节点状态相关处理   
 */
    
    private void markAlive(InetSocketAddress addr, EndpointState localState)
    {
        if (logger.isTraceEnabled())
            logger.trace("marking as alive {}" + addr);
        localState.markAlive();
        localState.updateTimestamp(); // prevents doStatusCheck from racing us and evicting if it was down > aVeryLongTime
        liveEndpoints.add(addr);
        unreachableEndpoints.remove(addr);
        logger.debug("removing expire time for endpoint : " + addr);
        logger.info("InetSocketAddress {} is now UP" + addr);
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
            subscriber.onAlive(addr, localState);
        if (logger.isTraceEnabled())
            logger.trace("Notified " + subscribers);
    }

    private void markDead(InetSocketAddress addr, EndpointState localState)
    {
        if (logger.isTraceEnabled())
            logger.trace("marking as dead {}"+ addr);
        localState.markDead();
        liveEndpoints.remove(addr);
        unreachableEndpoints.put(addr, System.currentTimeMillis());
        logger.info("InetSocketAddress {} is now dead." + addr);
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
            subscriber.onDead(addr, localState);
        if (logger.isTraceEnabled())
            logger.trace("Notified " + subscribers);
    }

    //由ApplicationState.STATUS来判断节点是否死掉。（主动remove一个节点的时候）
    private Boolean isDeadState(EndpointState epState)  
    {
        if (epState.getApplicationState(ApplicationState.STATUS) == null)
            return false;
        String value = epState.getApplicationState(ApplicationState.STATUS).value;
        String[] pieces = value.split(VersionedValue.DELIMITER_STR, -1);
        assert (pieces.length > 0);
        String state = pieces[0];
        for (String deadstate : DEAD_STATES)   //判断是否属于DEAD_STATES的某种状态
        {
            if (state.equals(deadstate))
                return true;
        }
        return false;
    }

    synchronized void setTOSNAndApplyLocalSubmit(LogProtos.Log log, List<SubmitDigest> submitDigests){
        gMaster.putLog(log.getMemberID(),log);
        String member = gMaster.check();
        while (member != null){
            LogProtos.Log.Builder builder = gMaster.setLogTOSN(member);
            if(!builder.getReceivedList().contains(GossiperDescriptor.getMemberId())){
                builder.addReceived(GossiperDescriptor.getMemberId());
            }

            submitRequests.add(builder.build());
            if(isMaster && builder.getMemberID().equals(GossiperDescriptor.getMemberId())){
                LogProtos.Log receivedLog = receivedRequests.getByTSM(builder.getTSM());
                if(receivedLog!= null){
                    receivedRequests.remove(receivedLog);
                }
                member = gMaster.check();
                continue;
            }
            Set<InetSocketAddress> set = new HashSet<InetSocketAddress>();
            set.add(FBUtilities.getLocalAddress());
            for(Entry<InetSocketAddress, String> entry : membersString.entrySet()){
                if(entry.getValue().equals(builder.getMemberID())){
                    set.add(entry.getKey());
                    break;
                }
            }
            submitDigests.add(new SubmitDigest(builder.getTOSN(), builder.getTSM(), builder.getMemberID(),set));
            member = gMaster.check();
        }
    }

    void getSendLogs(List<RequestDigest> requestDigests, List<LogProtos.Log> logs){
        for(RequestDigest requestDigest : requestDigests){
            LogProtos.Log log = submitRequests.getByTOSN(requestDigest.getTTSN());
            if(log != null){
                logs.add(log);
            }
        }
    }

    void applyLocalAck2MessageLocally(List<LogProtos.Log> logList){
        if(logList.size() > 0){
            for(LogProtos.Log log : logList){
                if(log.getTOSN() < currentTOSN.get()){
                    continue;
                }
                if(!log.getReceivedList().contains(membersString.get(FBUtilities.getLocalAddress()))){
                    LogProtos.Log.Builder builder = log.toBuilder();
                    builder.addReceived(membersString.get(FBUtilities.getLocalAddress()));
                    log = builder.build();
                }
                submitRequests.add(log);
            }
        }
    }
    void applyLogResponseTOSNMessage(SubmitDigest submitDigest){
        if(submitDigest != null){
            LogProtos.Log log = receivedRequests.getByTSM(submitDigest.getTSM());
            if(log != null){
                synchronized (log){
                    LogProtos.Log.Builder builder = log.toBuilder();
                    builder.setTOSN(submitDigest.getTOSN());
                    for(InetSocketAddress inetSocketAddress : submitDigest.getAccepted()){
                        if(!builder.getReceivedList().contains(membersString.get(inetSocketAddress))){
                            builder.addReceived(membersString.get(inetSocketAddress));
                        }
                    }
                    submitRequests.add(builder.build());
                    receivedRequests.remove(log);
                }
            }
        }
    }

    void applyLogsAckMessageLocally(List<SubmitDigest> submitDigestList, List<LogProtos.Log> logList){
        System.out.println("applyLogsAckMessageLocally------------------------------------------");
        if(submitDigestList.size() > 0){
            for(SubmitDigest submitDigest : submitDigestList){
                LogProtos.Log log = submitRequests.getByTOSN(submitDigest.getTOSN());
                if(log == null){
                    continue;
                }
                synchronized (log){
                    LogProtos.Log.Builder builder = log.toBuilder();
                    for(InetSocketAddress inetSocketAddress : submitDigest.getAccepted()){
                        if(!builder.getReceivedList().contains(membersString.get(inetSocketAddress))){
                            builder.addReceived(membersString.get(inetSocketAddress));
                        }
                    }
                    submitRequests.add(builder.build());
                }
            }
        }
        if(logList.size() > 0){
            for(LogProtos.Log log : logList){
                if(log.getTOSN() < currentTOSN.get()){
                    continue;
                }
                LogProtos.Log.Builder builder = log.toBuilder();
                if(!builder.getReceivedList().contains(membersString.get(FBUtilities.getLocalAddress()))){
                    builder.addReceived(membersString.get(FBUtilities.getLocalAddress()));
                }
                submitRequests.add(builder.build());
            }
        }
    }

    void applyStateLocally(Map<InetSocketAddress, EndpointState> epStateMap)
    {
        for (Entry<InetSocketAddress, EndpointState> entry : epStateMap.entrySet())
        {
        	InetSocketAddress ep = entry.getKey();
            if ( ep.equals(FBUtilities.getBroadcastAddress()))
                continue;
            if (justRemovedEndpoints.containsKey(ep))
            {
                if (logger.isTraceEnabled())
                    logger.trace("Ignoring gossip for " + ep + " because it is quarantined");
                continue;
            }

            EndpointState localEpStatePtr = endpointStateMap.get(ep);
            EndpointState remoteState = entry.getValue();
            /*
                If state does not exist just add it. If it does then add it if the remote generation is greater.
                If there is a generation tie, attempt to break it by heartbeat version.
            */
            if ( localEpStatePtr != null )
            {
            	int localGeneration = localEpStatePtr.getHeartBeatState().getGeneration();
            	int remoteGeneration = remoteState.getHeartBeatState().getGeneration();
                if (logger.isTraceEnabled())
                    logger.trace(ep + "local generation " + localGeneration + ", remote generation " + remoteGeneration);

            	if (remoteGeneration > localGeneration)
            	{
                    if (logger.isTraceEnabled())
                        logger.trace("Updating heartbeat state generation to " + remoteGeneration + " from " + localGeneration + " for " + ep);
                    // major state change will handle the update by inserting the remote state directly
                    handleMajorStateChange(ep, remoteState);
            	}
            	else if ( remoteGeneration == localGeneration ) // generation has not changed, apply new states
            	{
	                /* find maximum state */
	                int localMaxVersion = getMaxEndpointStateVersion(localEpStatePtr);
	                int remoteMaxVersion = getMaxEndpointStateVersion(remoteState);
	                if ( remoteMaxVersion > localMaxVersion )
	                {
                        // apply states, but do not notify since there is no major change
	                    applyNewStates(ep, localEpStatePtr, remoteState);
	                }
                    else if (logger.isTraceEnabled())
                            logger.trace("Ignoring remote version " + remoteMaxVersion + " <= " + localMaxVersion + " for " + ep);
                    if (!localEpStatePtr.isAlive() && !isDeadState(localEpStatePtr)) // unless of course, it was dead
                        markAlive(ep, localEpStatePtr);
            	}
                else
                {
                    if (logger.isTraceEnabled())
                        logger.trace("Ignoring remote generation " + remoteGeneration + " < " + localGeneration);
                }
            }
            else
            {
                // this is a new node, report it to the FD in case it is the first time we are seeing it AND it's not alive
                FailureDetector.instance.report(ep);
            	handleMajorStateChange(ep, remoteState);
            }
        }
    }

    /**
     * 大的改变,generation更新，把本地的EndpointState对象直接替换成新的（ConcurrentHashMap进行put操作，代价略高）
     * This method is called whenever there is a "big" change in ep state (a generation change for a known node).
     * @param ep endpoint
     * @param epState EndpointState for the endpoint
     */
    private void handleMajorStateChange(InetSocketAddress ep, EndpointState epState)
    {
        if (!isDeadState(epState))
        {
            if (endpointStateMap.get(ep) != null)
                logger.info("Node {} has restarted, now UP " + ep);
            else
                logger.info("Node {} is now part of the cluster " + ep);
        }
        if (logger.isTraceEnabled())
            logger.trace("Adding endpoint state for " + ep);
        endpointStateMap.put(ep, epState);

        // the node restarted: it is up to the subscriber to take whatever action is necessary
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
            subscriber.onRestart(ep, epState);

        if (!isDeadState(epState))
            markAlive(ep, epState);
        else
        {
            logger.debug("Not marking " + ep + " alive due to dead state");
            markDead(ep, epState);
        }
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
            subscriber.onJoin(ep, epState);
    }

    /***
     * 用从远程新获取的EndpointState对象里的每个值替换掉本地对象里的值（不涉及ConcurrentHashMap的put，效率较高）
     * @param addr
     * @param localState
     * @param remoteState
     */
    private void applyNewStates(InetSocketAddress addr, EndpointState localState, EndpointState remoteState)
    {
        // don't assert here, since if the node restarts the version will go back to zero
        int oldVersion = localState.getHeartBeatState().getHeartBeatVersion();

        localState.setHeartBeatState(remoteState.getHeartBeatState());
        if (logger.isTraceEnabled())
            logger.trace("Updating heartbeat state version to " + localState.getHeartBeatState().getHeartBeatVersion() + " from " + oldVersion + " for " + addr + " ...");

        for (Entry<ApplicationState, VersionedValue> remoteEntry : remoteState.getApplicationStateMapEntrySet())
        {
            ApplicationState remoteKey = remoteEntry.getKey();
            VersionedValue remoteValue = remoteEntry.getValue();

            assert remoteState.getHeartBeatState().getGeneration() == localState.getHeartBeatState().getGeneration();
            localState.addApplicationState(remoteKey, remoteValue);
            doNotifications(addr, remoteKey, remoteValue);
        }
    }

    // notify that an application state has changed
    private void doNotifications(InetSocketAddress addr, ApplicationState state, VersionedValue value)
    {
        for (IEndpointStateChangeSubscriber subscriber : subscribers)
        {
            subscriber.onChange(addr, state, value);
        }
    }

    
    
    
    
    /* 创建消息时，要求对方节点把某个EndpointState的完整信息发送过来（因对方的版本新）*/
    /* Request all the state for the endpoint in the gDigest */
    private void requestAll(GossipDigest gDigest, List<GossipDigest> deltaGossipDigestList, int remoteGeneration)
    {
        /* We are here since we have no data for this endpoint locally so request everthing. */
        deltaGossipDigestList.add( new GossipDigest(gDigest.getEndpoint(), remoteGeneration, 0) );
        if (logger.isTraceEnabled())
            logger.trace("requestAll for " + gDigest.getEndpoint());
    }

    /* 创建消息时，自己把某个EndpointState的完整信息添加到消息中（因本地的版本新）*/
    /* Send all the data with version greater than maxRemoteVersion */
    private void sendAll(GossipDigest gDigest, Map<InetSocketAddress, EndpointState> deltaEpStateMap, int maxRemoteVersion)
    {
        EndpointState localEpStatePtr = getStateForVersionBiggerThan(gDigest.getEndpoint(), maxRemoteVersion) ;
        if ( localEpStatePtr != null )
            deltaEpStateMap.put(gDigest.getEndpoint(), localEpStatePtr);
    }

    public void updateMemberString(String member, InetSocketAddress from){
        if(!membersString.containsKey(from)){
            membersString.put(from, member);
        }
    }

     boolean checkLogs(SubmitDigest submitDigest){
        if(GossiperDescriptor.getMemberId().equals(submitDigest.getMemberId()))
            return receivedRequests.contains(submitDigest.getTSM());
        else
            return  false;
    }

    void checkSubmitLogsAndApply(){
        if(!isUsed.compareAndSet(false,true)){
            return;
        }
        synchronized (submitRequests){
            if(submitRequests.size() <= 0){
                isUsed.set(false);
                return;
            }
            LogProtos.Log log = submitRequests.getFirst();
            if(log.getTOSN() < currentTOSN.get()){
                submitRequests.remove(log);
            }

            //System.out.println("-------------------local: ->" + log.getTOSN() + " ,current->" + currentTOSN.get());
            while (currentTOSN.get() == log.getTOSN() && membersString.size() == 3){
                if(log.getReceivedList().size() != membersString.size()){
                    break;
                }

                if(executeRequests.size() > 1000){
                    synchronized (executeRequests){
                        executeRequests.clear();
                        executeRequests.add(log);
                    }
                }else {

                    executeRequests.add(log);
                }
                submitRequests.remove(log);
                writeServer.enqueue(log);
                //实验
                /*
                long time_now = System.currentTimeMillis();
                long time_received = Long.parseLong(log.getDate());
                long delat = time_now - time_received;
                if(log.getMemberID().equals(GossiperDescriptor.getMemberId())){
                    synchronized (count){
                        if(count.containsKey(delat)){
                            count.put(delat,count.get(delat)+1);
                        }else {
                            count.put(delat,(long)1);
                        }
                    }
                }
                */
                //实验
                currentTOSN.getAndIncrement();

                //System.out.println("SubmitLogs-------------------------------:" + submitRequests.size());
                //System.out.println("checkSubmitLogsAndApply-------------------------------:" + currentTOSN.get());
                if(submitRequests.size() > 0){
                    log = submitRequests.getFirst();
                }
            }
            isUsed.set(false);
        }
    }

    void examineLogsDigest(List<RequestDigest> expectedRequests, List<SubmitDigest> remoteSubmit, List<SubmitDigest> deltaSubmit, long remoteMaxExecute, List<LogProtos.Log> sendLogs){
        long localMaxExecute = expectedRequests.size() > 0 ? executeRequests.getLast().getTOSN(): -1;
        if(localMaxExecute < remoteMaxExecute){
            submitRequests.examine(remoteMaxExecute, membersString.values());
        }else if(localMaxExecute > remoteMaxExecute){
            for(int i = 0; i < remoteSubmit.size(); i++){
                SubmitDigest remote = remoteSubmit.get(i);
                if(remote.getTOSN() <= localMaxExecute){
                    for(InetSocketAddress member : membersString.keySet()){
                        if(!remote.getAccepted().contains(member)){
                            remote.getAccepted().add(member);
                        }
                    }
                }else {
                    break;
                }
            }
        }
        synchronized (submitRequests) {
            int i, j;
            for (i = 0, j = 0; i < submitRequests.size() || j < remoteSubmit.size(); ) {
                LogProtos.Log local = null;
                if (i < submitRequests.size()) {
                    local = submitRequests.get(i);
                }
                SubmitDigest remote = null;
                if (j < remoteSubmit.size()) {
                    remote = remoteSubmit.get(j);
                }
                //System.out.println("-----------------submitRequest:size:" + submitRequests.size());
                //System.out.println("++++++++++Remote :( " + remote + ")");
                //System.out.println("++++++++++Local :( " + i + ")");
                if (i < submitRequests.size() && j < remoteSubmit.size()) {
                    //System.out.println("TOSN:(lcoal->" + local.getTOSN() + ",  remote->" + remote.getTOSN()+")");
                    if (local.getTOSN() == remote.getTOSN()) {
                        boolean flag = false;

                        Set<InetSocketAddress> tempSet = new HashSet<InetSocketAddress>(remote.getAccepted());
                        for(InetSocketAddress inetSocketAddress : tempSet){
                            if(!local.getReceivedList().contains(membersString.get(inetSocketAddress))){
                                flag = true;
                                remote.getAccepted().add(inetSocketAddress);
                            }
                        }
                        if(flag){
                            deltaSubmit.add(remote);
                        }
                        flag = false;
                        LogProtos.Log.Builder builder = local.toBuilder();
                        for (InetSocketAddress inetSocketAddress : remote.getAccepted()) {
                            if (!builder.getReceivedList().contains(membersString.get(inetSocketAddress))) {
                                builder.addReceived(membersString.get(inetSocketAddress));
                                flag = true;
                            }
                        }
                        if(flag){
                            submitRequests.add(builder.build());
                        }
                        j++;
                        i++;
                    } else if (local.getTOSN() < remote.getTOSN()) {
                        if(local.getTOSN() > remoteMaxExecute)
                            sendLogs.add(local);
                        i++;
                    } else {
                        //判断remote是否在ReceivedUpdates里面
                        if (checkLogs(remote)) {
                            //添加到submit链表
                            LogProtos.Log node = receivedRequests.getByTSM(remote.getTSM());
                            LogProtos.Log.Builder builder = node.toBuilder();
                            for(InetSocketAddress address : remote.getAccepted()){
                                if(!builder.getReceivedList().contains(membersString.get(address))){
                                    builder.addReceived(membersString.get(address));
                                }
                            }
                            builder.setTOSN(remote.getTOSN());
                            submitRequests.add(builder.build());
                            receivedRequests.remove(node);
                        } else {
                            if(currentTOSN.get() <= remote.getTOSN())
                                expectedRequests.add(new RequestDigest(remote.getMemberId(), remote.getTOSN()));
                        }
                        j++;
                    }
                } else if (i < submitRequests.size() && j == remoteSubmit.size()) {
                    if(local.getTOSN() > remoteMaxExecute)
                        sendLogs.add(local);
                    i++;
                } else if (i == submitRequests.size() && j < remoteSubmit.size()) {
                    //判断remote是否在received
                    if (checkLogs(remote)) {
                        LogProtos.Log node = receivedRequests.getByTSM(remote.getTSM());
                        LogProtos.Log.Builder builder = node.toBuilder();
                        for(InetSocketAddress address : remote.getAccepted()){
                            if(!builder.getReceivedList().contains(membersString.get(address))){
                                builder.addReceived(membersString.get(address));
                            }
                        }
                        builder.setTOSN(remote.getTOSN());
                        submitRequests.add(builder.build());
                        receivedRequests.remove(node);
                    } else {
                        if(currentTOSN.get() <= remote.getTOSN())
                            expectedRequests.add(new RequestDigest(remote.getMemberId(), remote.getTOSN()));
                    }
                    j++;
                }
            }
        }

    }

    /*
     * 第一次收到消息时进行检查，
     * 找出本地比对方新的节点，放到deltaEpStateMap中；
     * 找出对方比本地新的节点，放到deltaGossipDigestList中。
        This method is used to figure the state that the Gossiper has but Gossipee doesn't. The delta digests
        and the delta state are built up.
    */
    void examineGossiper(List<GossipDigest> gDigestList, List<GossipDigest> deltaGossipDigestList, Map<InetSocketAddress, EndpointState> deltaEpStateMap)
    {
        for ( GossipDigest gDigest : gDigestList )
        {
            int remoteGeneration = gDigest.getGeneration();
            int maxRemoteVersion = gDigest.getMaxVersion();
            /* Get state associated with the end point in digest */
            EndpointState epStatePtr = endpointStateMap.get(gDigest.getEndpoint());
            /*
                Here we need to fire a GossipDigestAckMessage. If we have some data associated with this endpoint locally
                then we follow the "if" path of the logic. If we have absolutely nothing for this endpoint we need to
                request all the data for this endpoint.
            */
            if ( epStatePtr != null )
            {
                int localGeneration = epStatePtr.getHeartBeatState().getGeneration();
                /* get the max version of all keys in the state associated with this endpoint */
                int maxLocalVersion = getMaxEndpointStateVersion(epStatePtr);
                if ( remoteGeneration == localGeneration && maxRemoteVersion == maxLocalVersion )
                    continue;

                if ( remoteGeneration > localGeneration )
                {
                    /* we request everything from the gossiper */
                    requestAll(gDigest, deltaGossipDigestList, remoteGeneration);
                }
                else if ( remoteGeneration < localGeneration )
                {
                    /* send all data with generation = localgeneration and version > 0 */
                    sendAll(gDigest, deltaEpStateMap, 0);
                }
                else if ( remoteGeneration == localGeneration )
                {
                    /*
                        If the max remote version is greater then we request the remote endpoint send us all the data
                        for this endpoint with version greater than the max version number we have locally for this
                        endpoint.
                        If the max remote version is lesser, then we send all the data we have locally for this endpoint
                        with version greater than the max remote version.
                    */
                    if ( maxRemoteVersion > maxLocalVersion )
                    {
                        deltaGossipDigestList.add( new GossipDigest(gDigest.getEndpoint(), remoteGeneration, maxLocalVersion) );
                    }
                    else if ( maxRemoteVersion < maxLocalVersion )
                    {
                        /* send all data with generation = localgeneration and version > maxRemoteVersion */
                        sendAll(gDigest, deltaEpStateMap, maxRemoteVersion);
                    }
                }
            }
            else
            {
                /* We are here since we have no data for this endpoint locally so request everything. */
                requestAll(gDigest, deltaGossipDigestList, remoteGeneration);
            }
        }
    }

    
    
    /**
     * 启动相关
     * Start the gossiper with the generation # retrieved from the System
     * table
     */
    public void start(int generationNbr)
    {
        /* Get the seeds from the config and initialize them. */
        Set<InetSocketAddress> seedHosts = GossiperDescriptor.getSeeds();
        for (InetSocketAddress seed : seedHosts)
        {
            if (seed.equals(FBUtilities.getBroadcastAddress()))
                continue;
            seeds.add(seed);
        }
        masterAddress = GossiperDescriptor.getMaster();
        if(masterAddress.equals(FBUtilities.getBroadcastAddress())){
            isMaster = true;
            gMaster = new GossipMaster();
        }

        writeServer = new WriteServer();
        writeServer.start();


        /* initialize the heartbeat state for this localEndpoint */
        maybeInitializeLocalState(generationNbr);
        EndpointState localState = endpointStateMap.get(FBUtilities.getBroadcastAddress());

        //notify snitches that Gossiper is about to start
        for (IApplicationStateStarting applicationstatestarting : applicationstatestartings) {
        	applicationstatestarting.gossiperStarting();
		}
        if (logger.isTraceEnabled())
            logger.trace("gossip started with generation " + localState.getHeartBeatState().getGeneration());

        scheduledGossipTask = executor.scheduleWithFixedDelay(new GossipTask(),
                                                              Gossiper.intervalInMillis,
                                                              Gossiper.intervalInMillis,
                                                              TimeUnit.MILLISECONDS);
    }
    

    //启动时把本地EndpointState初始化，放入endpointStateMap
    // initialize local HB state if needed. 
    public void maybeInitializeLocalState(int generationNbr)
    {
        EndpointState localState = endpointStateMap.get(FBUtilities.getBroadcastAddress());
        if ( localState == null )
        {
            HeartBeatState hbState = new HeartBeatState(generationNbr);
            localState = new EndpointState(hbState);
            localState.markAlive();
            endpointStateMap.put(FBUtilities.getBroadcastAddress(), localState);
        }
    }


	/***
	 * 优雅关闭，不再处理新消息
	 */
    public void stop()
    {
        scheduledGossipTask.cancel(false);
        logger.info("Announcing shutdown");
        try
        {
            Thread.sleep(intervalInMillis);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        MessageProducer prod = new MessageProducer()
        {
            public Message getMessage() throws IOException
            {
                return makeGossipShutdownMessage();
            }
        };
        for (InetSocketAddress ep : liveEndpoints)
        {
            try
            {
                MessagingService.instance().sendOneWay(prod.getMessage(), ep);
            }
            catch (IOException ex)
            {
                // keep going
            }
        }
    }

    public boolean isEnabled()
    {
        return !scheduledGossipTask.isCancelled();
    }

    
    
    
    
    
    /**
     * 测试使用
     * This should *only* be used for testing purposes.
     */
    public void initializeNodeUnsafe(InetSocketAddress addr, int generationNbr) {
        /* initialize the heartbeat state for this localEndpoint */
        EndpointState localState = endpointStateMap.get(addr);
        if ( localState == null )
        {
            HeartBeatState hbState = new HeartBeatState(generationNbr);
            localState = new EndpointState(hbState);
            localState.markAlive();
            endpointStateMap.put(addr, localState);
            //add for received updates
            //receivedRequests.put(addr, new SynLinkedList());
        }
    }

    /**
     * 尚无调用  StorageService的initserver有用到，系统重启后读取本地持久化旧数据可以这么处理；收到未知版本的消息也可如此处理
     * Add an endpoint we knew about previously, but whose state is unknown
     */
    public void addSavedEndpoint(InetSocketAddress ep)
    {
        if (ep.equals(FBUtilities.getBroadcastAddress()))
        {
            logger.debug("Attempt to add self as saved endpoint");
            return;
        }
        EndpointState epState = new EndpointState(new HeartBeatState(0));
        epState.markDead();
        endpointStateMap.put(ep, epState);
        unreachableEndpoints.put(ep, System.currentTimeMillis());
        if (logger.isTraceEnabled())
            logger.trace("Adding saved endpoint " + ep + " " + epState.getHeartBeatState().getGeneration());
    }

    
    /**
     * 尚无调用  StorageService的initserver，joinTokenRing可以用到
     * Remove the Endpoint and evict immediately, to avoid gossiping about this node.
     * This should only be called when a token is taken over by a new IP address.
     * 仅供新ip接管时使用，只做了remove操作
     * @param endpoint The endpoint that has been replaced
     */
    public void replacedEndpoint(InetSocketAddress endpoint)
    {
        removeEndpoint(endpoint);
        evictFromMembership(endpoint);
    }

    /**
     * 尚无调用
     * This method will begin removing an existing endpoint from the cluster by spoofing its state
     * This should never be called unless this coordinator has had 'removetoken' invoked
     * 尝试把HeartBeatState的Generation加一，不做其它处理？？？
     * @param endpoint - the endpoint being removed
     */
    public void advertiseRemoving(InetSocketAddress endpoint)
    {
        EndpointState epState = endpointStateMap.get(endpoint);
        // remember this node's generation
        int generation = epState.getHeartBeatState().getGeneration();
//        logger.info("Removing token: " + token);
        logger.info("Sleeping for " + GossiperDescriptor.getRing_delay() + "ms to ensure " + endpoint + " does not change");
        try
        {
            Thread.sleep(GossiperDescriptor.getRing_delay());
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
        // make sure it did not change
        epState = endpointStateMap.get(endpoint);
        if (epState.getHeartBeatState().getGeneration() != generation)
            throw new RuntimeException("Endpoint " + endpoint + " generation changed while trying to remove it");
        // update the other node's generation to mimic it as if it had changed it itself
        logger.info("Advertising removal for " + endpoint);
        epState.updateTimestamp(); // make sure we don't evict it too soon
        epState.getHeartBeatState().forceNewerGenerationUnsafe();
        endpointStateMap.put(endpoint, epState);
    }

    /**
     * 尚无调用
     * Handles switching the endpoint's state from REMOVING_TOKEN to REMOVED_TOKEN
     * This should only be called after advertiseRemoving
     *  尝试把HeartBeatState的Generation加一，不做其它处理？？？
    * @param endpoint
     */
    public void advertiseTokenRemoved(InetSocketAddress endpoint)
    {
        EndpointState epState = endpointStateMap.get(endpoint);
        epState.updateTimestamp(); // make sure we don't evict it too soon
        epState.getHeartBeatState().forceNewerGenerationUnsafe();
        logger.info("Completing removal of " + endpoint);
        endpointStateMap.put(endpoint, epState);
        // ensure at least one gossip round occurs before returning
        try
        {
            Thread.sleep(intervalInMillis * 2);
        }
        catch (InterruptedException e)
        {
            throw new AssertionError(e);
        }
    }

    /**
     * 尚无调用
     * @param endpoint
     * @return
     */
    public boolean isKnownEndpoint(InetSocketAddress endpoint)
    {
        return endpointStateMap.containsKey(endpoint);
    }

    /**
     * 尚无调用  实现GossiperMBean
     */
    public int getCurrentGenerationNumber(String address) throws UnknownHostException
    {
        return getCurrentGenerationNumber(InetSocketAddressUtil.parseInetSocketAddress(address));
    }
    
    /**
     * 尚无调用(间接) 
     */
    public int getCurrentGenerationNumber(InetSocketAddress endpoint)
    {
    	return endpointStateMap.get(endpoint).getHeartBeatState().getGeneration();
    }

    /**
     *  尚无调用  实现GossiperMBean
     */
    public long getEndpointDowntime(String address) throws UnknownHostException
    {
        return getEndpointDowntime(InetSocketAddressUtil.parseInetSocketAddress(address));
    }

    /**
     * 尚无调用(间接)
     */
    public long getEndpointDowntime(InetSocketAddress ep)
    {
        Long downtime = unreachableEndpoints.get(ep);
        if (downtime != null)
            return System.currentTimeMillis() - downtime;
        else
            return 0L;
    }

    /***
     * 尚无调用
     * determine which endpoint started up earlier
     * 比较它们的Generation
     * @param addr1
     * @param addr2
     * @return
     */
    public int compareEndpointStartup(InetSocketAddress addr1, InetSocketAddress addr2)
    {
        EndpointState ep1 = getEndpointStateForEndpoint(addr1);
        EndpointState ep2 = getEndpointStateForEndpoint(addr2);
        assert ep1 != null && ep2 != null;
        return ep1.getHeartBeatState().getGeneration() - ep2.getHeartBeatState().getGeneration();
    }
    
    /**
     * 其它服务调用，按一定时间间隔，更新本地ApplicationState的值，如机器负载、权重信息
     * @param state
     * @param value
     */
    public void addLocalApplicationState(ApplicationState state, VersionedValue value)
    {
        EndpointState epState = endpointStateMap.get(FBUtilities.getBroadcastAddress());
        assert epState != null;
        epState.addApplicationState(state, value);
        doNotifications(FBUtilities.getBroadcastAddress(), state, value);
    }
    
    public void addLocalApplicationState(ApplicationState state, String value){
    	addLocalApplicationState(state, VersionedValue.VersionedValueFactory.instance.getVersionedValue(value));
    }


}
