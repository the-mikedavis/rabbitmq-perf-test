// Copyright (c) 2022 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.perf.metrics;

import java.time.Duration;

/**
 * Contract to collect metrics during a run.
 *
 * @since 2.19.0
 */
public interface PerformanceMetrics {

  PerformanceMetrics NO_OP = new PerformanceMetrics() {
    @Override
    public void start() {

    }

    @Override
    public void published() {

    }

    @Override
    public void confirmed(int count, long[] latencies) {

    }

    @Override
    public void nacked(int count) {

    }

    @Override
    public void returned() {

    }

    @Override
    public void received(long latency) {

    }

    @Override
    public Duration interval() {
      return Duration.ZERO;
    }

    @Override
    public void resetGlobals() {

    }
  };

  void start();

  void published();

  void confirmed(int count, long[] latencies);

  void nacked(int count);

  void returned();

  void received(long latency);

  Duration interval();

  void resetGlobals();
}
