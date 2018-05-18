/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.scheduler.cluster.k8s

import java.io.Closeable

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.{KubernetesClient, KubernetesClientException, Watcher}
import io.fabric8.kubernetes.client.Watcher.Action

import org.apache.spark.deploy.k8s.Constants._
import org.apache.spark.internal.Logging
import org.apache.spark.util.Utils

private[spark] class ExecutorPodsWatchEventSource(
    eventHandler: ExecutorPodsEventHandler,
    kubernetesClient: KubernetesClient) extends Logging {

  private var watchConnection: Closeable = _

  def start(applicationId: String): Unit = {
    require(watchConnection == null, "Cannot start the watcher twice.")
    watchConnection = kubernetesClient.pods()
      .withLabel(SPARK_APP_ID_LABEL, applicationId)
      .withLabel(SPARK_POD_EXECUTOR_ROLE)
      .watch(new ExecutorPodsWatcher())
  }

  def stop(): Unit = {
    if (watchConnection != null) {
      Utils.tryLogNonFatalError {
        watchConnection.close()
      }
      watchConnection = null
    }
  }

  private class ExecutorPodsWatcher extends Watcher[Pod] {
    override def eventReceived(action: Action, pod: Pod): Unit = {
      eventHandler.sendUpdatedPodMetadata(pod)
    }

    override def onClose(e: KubernetesClientException): Unit = {
      logWarning("Kubernetes client has been closed (this is expected if the application is" +
        " shutting down.)", e)
    }
  }

}