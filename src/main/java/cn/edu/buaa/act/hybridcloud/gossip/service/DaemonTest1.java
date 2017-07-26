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

package cn.edu.buaa.act.hybridcloud.gossip.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import cn.edu.buaa.act.hybridcloud.gossip.concurrent.DebuggableScheduledThreadPoolExecutor;
import cn.edu.buaa.act.hybridcloud.gossip.config.GossipConfig;
import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.config.ParesConfig;
import cn.edu.buaa.act.hybridcloud.gossip.gms.ClientServer;
import cn.edu.buaa.act.hybridcloud.gossip.gms.Gossiper;
import cn.edu.buaa.act.hybridcloud.gossip.gms.LogRequireTOSNMessage;
import cn.edu.buaa.act.hybridcloud.gossip.gms.WriteServer;
import cn.edu.buaa.act.hybridcloud.gossip.locator.IApplicationStateStarting;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.proto.RequestProtos;
import cn.edu.buaa.act.hybridcloud.gossip.locator.EndpointSnitch;
import cn.edu.buaa.act.hybridcloud.gossip.net.Message;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import cn.edu.buaa.act.hybridcloud.gossip.utils.Consts;
import com.google.protobuf.ByteString;

import static java.lang.Thread.sleep;


/**
 * This class supports two methods for creating a Cassandra node daemon, 
 * invoking the class's main method, and using the jsvc wrapper from 
 * commons-daemon, (for more information on using this class with the 
 * jsvc wrapper, see the 
 * <a href="http://commons.apache.org/daemon/jsvc.html">Commons Daemon</a>
 * documentation).
 */

public class DaemonTest1
{
	static DebuggableScheduledThreadPoolExecutor executor,executor2;
    static int count = 0;
    private void printEndpointStates() throws IOException
    {
        executor = new DebuggableScheduledThreadPoolExecutor("abc");
        executor.scheduleWithFixedDelay(new Runnable(){
        	public void run(){
        		/*
        		Set<Entry<InetSocketAddress, EndpointState>> set =  Gossiper.instance.getEndpointStates();
        		for (Iterator<Entry<InetSocketAddress, EndpointState>>  iterator = set.iterator(); iterator.hasNext();) {
					Entry<InetSocketAddress, EndpointState> entry = iterator.next();
					System.out.println("key:"+entry.getKey()+", value:"+entry.getValue());
					
					EndpointState endpoint = entry.getValue();
					for (Entry<ApplicationState,VersionedValue>  entry2 : endpoint.getApplicationStateMapEntrySet()) {
						System.out.println("VersionedValue----key:"+entry2.getKey()+", value:"+entry2.getValue());
					}
				}
				*/
				/*
				System.out.println("---------size()" + Gossiper.instance.getMembersString().size());
				for(Entry<InetSocketAddress, String> entry: Gossiper.instance.getMembersString().entrySet()){
					System.out.println(entry.getKey() + "->" +entry.getValue());
				}
        		System.out.println("=======================" + Gossiper.instance.isMaster);
				if(Gossiper.instance.isMaster) {
					System.out.println("master:" + Gossiper.getgMaster().getMembers().size());
				}
				if(count != Gossiper.instance.count.size()){
					System.out.println("Counts" + Gossiper.instance.count.size());
					count = Gossiper.instance.count.size();
					StringBuffer value = new StringBuffer();
					for(Entry<Long,Long> entry: Gossiper.instance.count.entrySet()){
						value.append(entry.getKey() + " " + entry.getValue() +"\n");
					}
                    FileWrite.instatence.writeToFile("tmp.dat",0,value.toString().getBytes());
				}
				*/
				System.out.println("ReceivedLog: " + Gossiper.instance.getReceivedRequests().size());
				//System.out.println("SubmitLog: " + Gossiper.instance.getSubmitRequests().size());
				//System.out.println("ExecutedLog: " + Gossiper.instance.getExecuteRequests().size());
				System.out.println("TSM: " + ClientServer.instance.getNextTSM() + "  TOSN :" + Gossiper.instance.getCurrentTOSN() + "  count: " + WriteServer.count);
        		Set<InetSocketAddress> liveset =  Gossiper.instance.getLiveMembers();
				if(liveset.size() != 3){
        			for (Iterator<InetSocketAddress> iterator = liveset.iterator(); iterator.hasNext();) {
        				InetSocketAddress inetAddress = (InetSocketAddress) iterator.next();
						System.out.println(inetAddress);
					}
				}
        	}
        },
        Gossiper.intervalInMillis*5,
        Gossiper.intervalInMillis*5,
        TimeUnit.MILLISECONDS);

        executor2 = new DebuggableScheduledThreadPoolExecutor("def");
        executor2.scheduleWithFixedDelay(new Runnable(){
        	public void run(){

 //           	Gossiper.instance.addLocalApplicationState(ApplicationState.STATUS,String.valueOf((System.currentTimeMillis()/1000)));
            	//Gossiper.instance.addLocalApplicationState(ApplicationState.WEIGHT,VersionedValueFactory.instance.weight(50));
        		//System.out.println("++++++++++++++++++++++++++++++++++");
        		
        	}
        },
        1,
        3,
        TimeUnit.SECONDS);
        
    }
    
    public static void main(String[] args) throws IOException, InterruptedException
    {

		Properties properties = new ParesConfig().getProperties();
		String listen_address = properties.getProperty(Consts.CONST_LISTEN_ADDRESS_MEMBER);
		String seeds = properties.getProperty(Consts.CONST_SEEDS);
		String master = properties.getProperty(Consts.CONST_GOSSIP_MASTER);
		String memberId = properties.getProperty(Consts.CONST_MEMBER_ID);
		String listen_client = properties.getProperty(Consts.CONST_LISTEN_ADDRESS_CLIENT);
		String mount_point = properties.getProperty(Consts.CONST_MOUNT_POINT);

		GossiperDescriptor.init(new GossipConfig(listen_address,seeds, master,memberId,listen_client,mount_point));

		//GossiperDescriptor.init(new GossipConfig("localhost:9001","localhost:9001,localhost:9002,localhost:9003", "localhost:9001","Member01","localhost:8001",System.getProperty("user.dir")));
		//TODO
    	final GossipService service = new GossipService();

    	service.getGossiper().register(new EndpointSnitch());
    	service.getGossiper().register(new IApplicationStateStarting(){
    		public void gossiperStarting(){
    	    	//service.getGossiper().addLocalApplicationState(ApplicationState.LOAD,VersionedValueFactory.instance.load(1.1));
    	    	//service.getGossiper().addLocalApplicationState(ApplicationState.WEIGHT,VersionedValueFactory.instance.weight(1));

    		}
    	});
    	
    	service.start((int)(System.currentTimeMillis() / 1000));
		new DaemonTest1().printEndpointStates();
		//sleep(15000);
		RequestProtos.Request.Builder builder = RequestProtos.Request.newBuilder();
		builder.setValue(ByteString.copyFrom("cao".getBytes()));
		builder.setId("ID");
		builder.setFileName("tmp.dat");
		LogProtos.Log.Builder logBuilder = LogProtos.Log.newBuilder();
		logBuilder.setMemberID("Member01");
		logBuilder.addReceived("Member01");
		logBuilder.setUpdate(builder.build());
		for(int i = 0; i < 0; i++){
			sleep(10);
			logBuilder.setTSM(i);
			logBuilder.setTOSN(-1);
			LogRequireTOSNMessage message = new LogRequireTOSNMessage(logBuilder.build());
			Message received = Gossiper.instance.makeLogRequireTOSNMessage(message);
			try{
				if(Gossiper.instance.getReceivedRequests().size() > 1000){
					i--;
				}else {
					Gossiper.instance.getReceivedRequests().add(logBuilder.build());
					MessagingService.instance().sendOneWay(received, GossiperDescriptor.getMaster());
				}
			}catch (Exception e){
				executor.shutdown();
				executor2.shutdown();
				service.stop();
			}
		}
		/*
		logBuilder.setDate(String.valueOf(System.currentTimeMillis()));
		logBuilder.setMemberID("Member01");
		logBuilder.setTOSN(0);
		logBuilder.setTSM(0);
		logBuilder.addReceived("Member01");
		logBuilder.setUpdate(builder.build());
        Gossiper.instance.getSubmitRequests().add(logBuilder.build());
		logBuilder.setDate(String.valueOf(System.currentTimeMillis()));
		logBuilder.setTOSN(1);
		logBuilder.setTSM(1);
		Gossiper.instance.getSubmitRequests().add(logBuilder.build());
		logBuilder.setDate(String.valueOf(System.currentTimeMillis()));
		logBuilder.setTOSN(5);
		logBuilder.setTSM(3);
		Gossiper.instance.getSubmitRequests().add(logBuilder.build());
		*/
//    	Thread.sleep(1000*15);
//    	executor.shutdownNow();
//    	executor2.shutdownNow();
//    	service.stop();

    }
}
