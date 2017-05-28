// Copyright 2016 Twitter. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.heron.packing.trevorpacking;

//import java.util.HashSet;
//import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.heron.api.generated.TopologyAPI;
//import com.twitter.heron.packing.AssertPacking;
import com.twitter.heron.packing.PackingUtils;
import com.twitter.heron.spi.common.ClusterDefaults;
import com.twitter.heron.spi.common.Config;
//import com.twitter.heron.spi.common.Constants;
import com.twitter.heron.spi.common.Keys;
import com.twitter.heron.spi.packing.PackingPlan;
//import com.twitter.heron.spi.packing.Resource;
import com.twitter.heron.spi.utils.TopologyTests;
import com.twitter.heron.spi.utils.TopologyUtils;

public class ILPPackingTest {
  private static final String BOLT_NAME = "bolt";
  private static final String SPOUT_NAME = "spout";
  private static final double DELTA = 0.1;

  private TopologyAPI.Topology getTopology(
      int spoutParallelism, int boltParallelism,
      com.twitter.heron.api.Config topologyConfig) {
    return TopologyTests.createTopology("testTopology", topologyConfig, SPOUT_NAME, BOLT_NAME,
        spoutParallelism, boltParallelism);
  }

  private PackingPlan getILPPackingPlan(TopologyAPI.Topology topology) {
    Config config = Config.newBuilder()
        .put(Keys.topologyId(), topology.getId())
        .put(Keys.topologyName(), topology.getName())
        .putAll(ClusterDefaults.getDefaults())
        .build();

    //ILPPacking packing = new ILPPacking("http://po3-heron01:8000");
    ILPPacking packing = new ILPPacking("http://localhost:8000");
    packing.initialize(config, topology);
    return packing.pack();
  }

  private static int getComponentCount(
      PackingPlan.ContainerPlan containerPlan, String componentName) {
    int count = 0;
    for (PackingPlan.InstancePlan instancePlan : containerPlan.getInstances()) {
      if (componentName.equals(PackingUtils.getComponentName(instancePlan.getId()))) {
        count++;
      }
    }
    return count;
  }

  private static void assertComponentCount(
      PackingPlan.ContainerPlan containerPlan, String componentName, int expectedCount) {
    Assert.assertEquals(expectedCount, getComponentCount(containerPlan, componentName));
  }

  /**
   * test packing of instances derived from files pulled over an http
   * connection
   */
  @Test
  public void testHttpPacking() throws Exception {
    int numContainers = 2;
    int componentParallelism = 4;

    // Set up the topology and its config
    com.twitter.heron.api.Config topologyConfig = new com.twitter.heron.api.Config();
    topologyConfig.put(com.twitter.heron.api.Config.TOPOLOGY_STMGRS, numContainers);

    TopologyAPI.Topology topology =
        getTopology(componentParallelism, componentParallelism, topologyConfig);

    int numInstance = TopologyUtils.getTotalInstance(topology);
    // Two components
    Assert.assertEquals(2 * componentParallelism, numInstance);
    PackingPlan output = getILPPackingPlan(topology);
    Assert.assertEquals(numContainers, output.getContainers().size());
    Assert.assertEquals((Integer) numInstance, output.getInstanceCount());

    for (PackingPlan.ContainerPlan container : output.getContainers()) {
      Assert.assertEquals(numInstance / numContainers, container.getInstances().size());

      // Verify containers got 4 spouts or 4 bolts
      //assertComponentCount(container, "spout", 2);
      //assertComponentCount(container, "bolt", 2);

      int spoutCount = getComponentCount(container, "spout");
      int boltCount = getComponentCount(container, "bolt");
      if (spoutCount > 0) {
        Assert.assertEquals(4, spoutCount);
        Assert.assertEquals(0, boltCount);
      } else {
        Assert.assertEquals(0, spoutCount);
        Assert.assertEquals(4, boltCount);
      }
    }
  }

}
