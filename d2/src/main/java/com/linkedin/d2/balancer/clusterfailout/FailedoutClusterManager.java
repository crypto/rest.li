/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.linkedin.d2.balancer.clusterfailout;

import com.linkedin.d2.balancer.LoadBalancerState;
import com.linkedin.d2.balancer.LoadBalancerState.LoadBalancerStateListenerCallback;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * This class is responsible for managing a failed out cluster.
 * Some example tasks include:
 * - Adding cluster and URI watches for the peer clusters.
 * - Establishing connections to instances in the peer clusters.
 * - Managing failout config updates for the cluster.
 */
public class FailedoutClusterManager<T extends ClusterFailoutConfig> {
  private final String _clusterName;
  private final LoadBalancerState _loadBalancerState;
  private final ConcurrentMap<String, LoadBalancerStateListenerCallback> _clusterListeners =
      new ConcurrentHashMap<>();
  private T _failoutConfig;

  public FailedoutClusterManager(@Nonnull String clusterName, @Nonnull LoadBalancerState loadBalancerState) {
    _clusterName = clusterName;
    _loadBalancerState = loadBalancerState;
  }

  public String getClusterName() {
    return _clusterName;
  }

  /**
   * Gets the current failout config.
   * @return Optional.empty() when there is not failout config found.
   */
  public Optional<T> getFailoutConfig() {
    return Optional.ofNullable(_failoutConfig);
  }

  /**
   * Updates to a new failout config version.
   * @param failoutConfig The new failout config. Null when there is not active failout associated with the cluster.
   */
  public void updateFailoutConfig(@Nullable T failoutConfig) {
    if (failoutConfig == null) {
      removePeerClusterWatches();
    } else {
      processNewConfig(failoutConfig);
    }

    _failoutConfig = failoutConfig;
  }

  private void processNewConfig(@Nonnull T failoutConfig) {
    if (!failoutConfig.isFailedOut()) {
      removePeerClusterWatches();
    } else {
      Set<String> peerClusters = failoutConfig.getPeerClusters();
      addPeerClusterWatches(peerClusters);
    }
  }

  /**
   * Calle this method when a cluster is failed out and/or its new peer clusters are identified.
   * @param newPeerClusters Name of the peer clusters of the failed out clusters
   */
  void addPeerClusterWatches(@Nonnull Set<String> newPeerClusters) {
    final Set<String> existingPeerClusters = _clusterListeners.keySet();

    if (newPeerClusters.isEmpty()) {
      removePeerClusterWatches();
      return;
    }

    final Set<String> peerClustersToAdd = new HashSet<>(newPeerClusters);
    peerClustersToAdd.removeAll(existingPeerClusters);

    if (!peerClustersToAdd.isEmpty()) {
      addClusterWatches(peerClustersToAdd);
    }

    final Set<String> peerClustersToRemove = new HashSet<>(existingPeerClusters);
    peerClustersToRemove.removeAll(newPeerClusters);

    if (!peerClustersToRemove.isEmpty()) {
      removeClusterWatches(peerClustersToRemove);
    }
  }

  /**
   * Call this method when a cluster failed out is over and we do not need to monitor its peer clusters.
   */
  void removePeerClusterWatches() {
    removeClusterWatches(_clusterListeners.keySet());
  }

  private void addClusterWatches(@Nonnull Set<String> clustersToWatch) {
    for (final String cluster : clustersToWatch) {
      _clusterListeners.computeIfAbsent(cluster, clusterName -> {
        // TODO(RESILIEN-50): Establish connections to peer clusters when listener#done() is invoked.
        LoadBalancerStateListenerCallback listener = new LoadBalancerState.NullStateListenerCallback();
        _loadBalancerState.listenToCluster(cluster, listener);
        return listener;
      });
    }
  }

  private void removeClusterWatches(@Nonnull Set<String> clustersToRemove) {
    for (String cluster : clustersToRemove) {
      final LoadBalancerStateListenerCallback listener = _clusterListeners.remove(cluster);
      if (listener != null) {
        // TODO(RESILIEN-51): Unregister watches when failout is over
      }
    }
  }
}
