package com.linkedin.d2.balancer.clusterfailout;

import com.linkedin.d2.balancer.LoadBalancerState;
import com.linkedin.d2.balancer.LoadBalancerState.LoadBalancerStateListenerCallback;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class FailedoutClusterWatchManager {
  private final Map<String, Set<String>> _failedoutClusters = new HashMap<>();
  private final LoadBalancerState _loadBalancerState;
  private final ConcurrentMap<String, LoadBalancerStateListenerCallback> _clusterListeners =
      new ConcurrentHashMap<>();

  public FailedoutClusterWatchManager(LoadBalancerState loadBalancerState) {
    _loadBalancerState = loadBalancerState;
  }

  public void addPeerClusterWatches(String clusterName, Set<String> newPeerClusters) {
    final Set<String> existingPeerClusters = _failedoutClusters.getOrDefault(clusterName, Collections.emptySet());

    if (newPeerClusters.isEmpty()) {
      removeClusterWatches(existingPeerClusters);
      _failedoutClusters.remove(clusterName);
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

    _failedoutClusters.put(clusterName, newPeerClusters);
  }

  public void removePeerClusterWatches(String clusterName) {
    final Set<String> existingPeerClusters = _failedoutClusters.getOrDefault(clusterName, Collections.emptySet());
    removeClusterWatches(existingPeerClusters);
    _failedoutClusters.remove(clusterName);
  }

  private void removeClusterWatches(Set<String> peerClustersToRemove) {
    for (String peerCluster : peerClustersToRemove) {
      final LoadBalancerStateListenerCallback listener = _clusterListeners.remove(peerCluster);
      if (listener != null) {
        // TODO(RESILIEN-51): Unregister watches when failout is over
      }
    }
  }

  private void addClusterWatches(Set<String> peerClusters) {
    for (final String peerCluster : peerClusters) {
      _clusterListeners.computeIfAbsent(peerCluster, clusterName -> {
        // TODO(RESILIEN-50): Establish connections to peer clusters when listener#done() is invoked.
        LoadBalancerStateListenerCallback listener = new LoadBalancerState.NullStateListenerCallback();
        _loadBalancerState.listenToCluster(peerCluster, listener);
        return listener;
      });
    }
  }
}
