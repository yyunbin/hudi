/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.metrics.prometheus;

import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.exception.HoodieException;
import org.apache.hudi.metrics.MetricsReporter;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PushGatewayMetricsReporter extends MetricsReporter {

  private final PushGatewayReporter pushGatewayReporter;
  private final int periodSeconds;
  private final boolean deleteShutdown;
  private final String configuredJobName;
  private final Map<String, String> configuredLabels;
  private final boolean randomSuffix;

  public PushGatewayMetricsReporter(HoodieWriteConfig config, MetricRegistry registry) {

    String serverHost = config.getPushGatewayHost();
    int serverPort = config.getPushGatewayPort();
    periodSeconds = config.getPushGatewayReportPeriodSeconds();
    deleteShutdown = config.getPushGatewayDeleteOnShutdown();
    configuredJobName = config.getPushGatewayJobName();
    configuredLabels = Collections.unmodifiableMap(parseLabels(config.getPushGatewayLabels()));
    randomSuffix = config.getPushGatewayRandomJobNameSuffix();

    pushGatewayReporter = new PushGatewayReporter(
        registry,
        MetricFilter.ALL,
        TimeUnit.SECONDS,
        TimeUnit.SECONDS,
        getJobName(),
        configuredLabels,
        serverHost,
        serverPort,
        deleteShutdown);
  }

  @Override
  public void start() {
    pushGatewayReporter.start(periodSeconds, TimeUnit.SECONDS);
  }

  @Override
  public void report() {
    pushGatewayReporter.report(null, null, null, null, null);
  }

  @Override
  public void stop() {
    pushGatewayReporter.stop();
  }

  public Map<String, String> getLabels() {
    return configuredLabels;
  }

  private String getJobName() {
    if (randomSuffix) {
      Random random = new Random();
      return configuredJobName + random.nextLong();
    }
    return configuredJobName;
  }

  private static Map<String, String> parseLabels(String labels) {
    Stream<String[]> intermediateStream = Pattern.compile("\\s*,\\s*")
        .splitAsStream(labels.trim())
        .map(s -> s.split(":", 2));

    Map<String, String> labelsMap = new HashMap<>();
    intermediateStream.forEach(a -> {
      String key = a[0];
      String value = a.length > 1 ? a[1] : "";
      String oldValue = labelsMap.put(key, value);
      if (oldValue != null) {
        throw new HoodieException(String.format("Duplicate key=%s found in labels: %s %s",
            key, key + ":" + oldValue, key + ":" + value));
      }
    });
    return labelsMap;
  }
}
