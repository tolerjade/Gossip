package cn.edu.buaa.act.hybridcloud.gossip.locator;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */


import java.net.InetSocketAddress;

import cn.edu.buaa.act.hybridcloud.gossip.gms.*;
import cn.edu.buaa.act.hybridcloud.gossip.gms.EndpointState;
import cn.edu.buaa.act.hybridcloud.gossip.gms.IEndpointStateChangeSubscriber;
import cn.edu.buaa.act.hybridcloud.gossip.gms.VersionedValue;

/**
 * 1) Snitch will automatically set the public IP by querying the AWS API
 *
 * 2) Snitch will set the private IP as a Gossip application state.
 *
 * 3) Snitch implements IESCS and will reset the connection if it is within the
 * same region to communicate via private IP.
 *
 * Implements Ec2Snitch to inherit its functionality and extend it for
 * Multi-Region.
 *
 * Operational: All the nodes in this cluster needs to be able to (modify the
 * Security group settings in AWS) communicate via Public IP's.
 */


/***
 * jydong add
 */
public class EndpointSnitch implements IEndpointStateChangeSubscriber
{

	@Override
	public void onAlive(InetSocketAddress endpoint, EndpointState state) {
		// TODO Auto-generated method stub
		System.out.println("onAlive!"+endpoint);
	}

	@Override
	public void onChange(InetSocketAddress endpoint, ApplicationState state,
			VersionedValue value) {
		// TODO Auto-generated method stub
		System.out.println("onChange!"+endpoint);
	}

	@Override
	public void onDead(InetSocketAddress endpoint, EndpointState state) {
		// TODO Auto-generated method stub
		System.out.println("onDead!"+endpoint);
	}

	@Override
	public void onJoin(InetSocketAddress endpoint, EndpointState epState) {
		// TODO Auto-generated method stub
		System.out.println("onJoin!"+endpoint);
	}

	@Override
	public void onRemove(InetSocketAddress endpoint) {
		// TODO Auto-generated method stub
		System.out.println("onRemove!"+endpoint);
	}

	@Override
	public void onRestart(InetSocketAddress endpoint, EndpointState state) {
		// TODO Auto-generated method stub
		System.out.println("onRestart!"+endpoint);
	}

}
