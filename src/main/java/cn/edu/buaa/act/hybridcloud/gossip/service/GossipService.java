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
import java.util.Properties;

import cn.edu.buaa.act.hybridcloud.gossip.config.GossipConfig;
import cn.edu.buaa.act.hybridcloud.gossip.config.GossiperDescriptor;
import cn.edu.buaa.act.hybridcloud.gossip.config.ParesConfig;
import cn.edu.buaa.act.hybridcloud.gossip.gms.ApplicationState;
import cn.edu.buaa.act.hybridcloud.gossip.gms.Gossiper;
import cn.edu.buaa.act.hybridcloud.gossip.gms.VersionedValue;
import cn.edu.buaa.act.hybridcloud.gossip.locator.EndpointSnitch;
import cn.edu.buaa.act.hybridcloud.gossip.locator.IApplicationStateStarting;
import cn.edu.buaa.act.hybridcloud.gossip.net.MessagingService;
import cn.edu.buaa.act.hybridcloud.gossip.net.NetSnitch;
import cn.edu.buaa.act.hybridcloud.gossip.utils.Consts;
import cn.edu.buaa.act.hybridcloud.gossip.utils.FBUtilities;
import org.apache.log4j.Logger;


/**
 * This class supports two methods for creating a Cassandra node daemon, 
 * invoking the class's main method, and using the jsvc wrapper from 
 * commons-daemon, (for more information on using this class with the 
 * jsvc wrapper, see the 
 * <a href="http://commons.apache.org/daemon/jsvc.html">Commons Daemon</a>
 * documentation).
 */

public class GossipService
{
    private static Logger logger = Logger.getLogger(GossipService.class);
    
    static{
    	Gossiper.instance.register(new NetSnitch());
    }
    
    public Gossiper getGossiper(){
    	return Gossiper.instance;
    }
    
    public void start(int generationNbr) throws IOException
    {
    	//打开gossip相关的Tcp连接服务，当前项目gossip所用通信暂且独立，没用到其它通信模块。
    	MessagingService.instance().listen(FBUtilities.getLocalAddress());
		MessagingService.instance().listenClient(GossiperDescriptor.getClientListen());
    	logger.info("Gossiper message service has been started...");
    	logger.info("Gossip starting up...");
    	Gossiper.instance.start(generationNbr);
		Gossiper.instance.updateMemberString(GossiperDescriptor.getMemberId(), FBUtilities.getLocalAddress());
    	logger.info("Gossip has been started...");
    }
    

    public void stop() throws IOException, InterruptedException
    {
    	Gossiper.instance.stop(); 
    	logger.info("Gossip has been stoped...");

    	//关闭gossip相关的Tcp连接，当前项目gossip所用通信暂且独立，没用到其它通信模块。
    	MessagingService.instance().shutdownAllConnections();
    	logger.info("All Gossiper connection has been closed...");
    }
    
    
    
    public static void main(String[] args) throws IOException, InterruptedException{
		Properties properties = new ParesConfig().getProperties();
		String listen_address = properties.getProperty(Consts.CONST_LISTEN_ADDRESS_MEMBER);
		String seeds = properties.getProperty(Consts.CONST_SEEDS);
		String master = properties.getProperty(Consts.CONST_GOSSIP_MASTER);
		String memberId = properties.getProperty(Consts.CONST_MEMBER_ID);
		String listen_client = properties.getProperty(Consts.CONST_LISTEN_ADDRESS_CLIENT);
		String mount_point = properties.getProperty(Consts.CONST_MOUNT_POINT);

		GossiperDescriptor.init(new GossipConfig(listen_address,seeds, master,memberId,listen_client,mount_point));
    	//GossiperDescriptor.init(new GossipConfig("localhost:9001","localhost:9001","localhost:9001","member","localhost:8001"));
		
    	final GossipService service = new GossipService();
   	
    	service.getGossiper().register(new EndpointSnitch());
    	service.getGossiper().register(new IApplicationStateStarting(){
    		public void gossiperStarting(){
    	    	service.getGossiper().addLocalApplicationState(ApplicationState.LOAD, VersionedValue.VersionedValueFactory.instance.load(7.1));
    	    	service.getGossiper().addLocalApplicationState(ApplicationState.WEIGHT, VersionedValue.VersionedValueFactory.instance.weight(5));

    		}
    	});
    	
    	service.start((int)(System.currentTimeMillis() / 1000));
    	
    }
    
}
