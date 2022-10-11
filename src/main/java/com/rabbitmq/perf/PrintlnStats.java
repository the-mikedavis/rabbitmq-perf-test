// Copyright (c) 2018-2022 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.perf;

import com.codahale.metrics.Histogram;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

/**
 * Class to output stats on the console and in a CSV file.
 */
class PrintlnStats extends Stats {
    private static final String LATENCY_HEADER = "min/median/75th/95th/99th";
    public static final String MESSAGE_RATE_LABEL = "msg/s";
    private final boolean sendStatsEnabled;
    private final boolean recvStatsEnabled;
    private final boolean returnStatsEnabled;
    private final boolean confirmStatsEnabled;
    private final boolean legacyMetrics;
    private final boolean useMillis;
    private final String units;

    private final String testID;
    private final PrintWriter csvOut;
    private final PrintStream out;

    private final AtomicBoolean printFinalOnGoingOrDone = new AtomicBoolean(false);

    public PrintlnStats(String testID, Duration interval,
                        boolean sendStatsEnabled, boolean recvStatsEnabled,
                        boolean returnStatsEnabled, boolean confirmStatsEnabled,
                        boolean legacyMetrics, boolean useMillis,
                        PrintWriter csvOut, MeterRegistry registry, String metricsPrefix) {
        this(testID, interval, sendStatsEnabled, recvStatsEnabled,
                returnStatsEnabled, confirmStatsEnabled,
                legacyMetrics, useMillis,
                csvOut, registry, metricsPrefix, System.out);
    }

    public PrintlnStats(String testID, Duration interval,
                        boolean sendStatsEnabled, boolean recvStatsEnabled,
                        boolean returnStatsEnabled, boolean confirmStatsEnabled,
                        boolean legacyMetrics, boolean useMillis,
                        PrintWriter csvOut, MeterRegistry registry, String metricsPrefix, PrintStream out) {
        super(interval, useMillis, registry, metricsPrefix);
        this.sendStatsEnabled = sendStatsEnabled;
        this.recvStatsEnabled = recvStatsEnabled;
        this.returnStatsEnabled = returnStatsEnabled;
        this.confirmStatsEnabled = confirmStatsEnabled;
        this.testID = testID;
        this.legacyMetrics = legacyMetrics;
        this.useMillis = useMillis;
        this.units = useMillis ? "ms" : "µs";
        this.csvOut = csvOut;
        this.out = out;

        if (this.csvOut != null) {
            this.csvOut.printf("id,time (s),published (msg/s),returned (msg/s)," +
                            "confirmed (msg/s),nacked (msg/s)," +
                            "received (msg/s),min consumer latency (%s),median consumer latency (%s)," +
                            "75th p. consumer latency (%s),95th p. consumer latency (%s),99th p. consumer latency (%s)," +
                            "min confirm latency (%s),median confirm latency (%s)," +
                            "75th p. confirm latency (%s),95th p. confirm latency (%s),99th p. confirm latency (%s)%n",
                    units, units, units, units, units, units, units, units, units, units);
        }
    }

    private static String formatRate(double rate) {
        if (rate == 0.0) return format("%d", (long) rate);
        else if (rate < 1) return format("%1.2f", rate);
        else if (rate < 10) return format("%1.1f", rate);
        else return format("%d", (long) rate);
    }

    private static String rate(double rate, boolean display) {
        if (display) {
            return formatRate(rate);
        } else {
            return "";
        }
    }

    private static double rate(long count, long elapsedInMs) {
        return MS_TO_SECOND * count / elapsedInMs;
    }

    @Override
    protected void report(long now) {
        if (!printFinalOnGoingOrDone.get()) {
            doReport(now);
        }
    }

    private void doReport(long now) {
        String output = "id: " + testID + ", ";

        double ratePublished = 0.0;
        double rateReturned = 0.0;
        double rateConfirmed = 0.0;
        double rateNacked = 0.0;
        double rateConsumed = 0.0;

        long elapsedTime = Duration.ofNanos(elapsedInterval.get()).toMillis();

        if (sendStatsEnabled) {
            ratePublished = rate(sendCountInterval.get(), elapsedTime);
            published(ratePublished);
        }
        if (sendStatsEnabled && returnStatsEnabled) {
            rateReturned = rate(returnCountInterval.get(), elapsedTime);
            returned(rateReturned);
        }
        if (sendStatsEnabled && confirmStatsEnabled) {
            rateConfirmed = rate(confirmCountInterval.get(), elapsedTime);
            confirmed(rateConfirmed);
        }
        if (sendStatsEnabled && confirmStatsEnabled) {
            rateNacked = rate(nackCountInterval.get(), elapsedTime);
            nacked(rateNacked);
        }
        if (recvStatsEnabled) {
            rateConsumed = rate(recvCountInterval.get(), elapsedTime);
            received(rateConsumed);
        }

        output += "time: " + format("%.3f", (now - startTime) / NANO_TO_SECOND) + "s";
        output +=
                getRate("sent", ratePublished, sendStatsEnabled) +
                        getRate("returned", rateReturned, sendStatsEnabled && returnStatsEnabled) +
                        getRate("confirmed", rateConfirmed, sendStatsEnabled && confirmStatsEnabled) +
                        getRate("nacked", rateNacked, sendStatsEnabled && confirmStatsEnabled) +
                        getRate("received", rateConsumed, recvStatsEnabled);

        long[] consumerLatencyStats = null;
        long[] confirmLatencyStats = null;
        if (legacyMetrics && latencyCountInterval.get() > 0) {
            output += legacyMetrics();
        } else {
            if (shouldDisplayConsumerLatency() || shouldDisplayConfirmLatency()) {
                output += format(", %s ", LATENCY_HEADER);
            }
            if (shouldDisplayConsumerLatency()) {
                consumerLatencyStats = getStats(latency);
                output += "consumer latency: " + formatLatency(consumerLatencyStats);
            }
            if (shouldDisplayConsumerLatency() && shouldDisplayConfirmLatency()) {
                output += ", ";
            }

            if (shouldDisplayConfirmLatency()) {
                confirmLatencyStats = getStats(confirmLatency);
                output += "confirm latency: " + formatLatency(confirmLatencyStats);
            }
        }

        if (!printFinalOnGoingOrDone.get()) {
            this.out.println(output);
        }

        writeToCsvIfNecessary(now, ratePublished, rateReturned, rateConfirmed, rateNacked,
            rateConsumed, consumerLatencyStats, confirmLatencyStats);
    }

    private String latencyReport(Histogram latency) {
        long[] stats = getStats(latency);
        return formatLatency(stats);
    }

    private String formatLatency(long [] stats) {
        return format("%d/%d/%d/%d/%d %s", stats[0], stats[1], stats[2], stats[3], stats[4],
            units);
    }

    private String legacyMetrics() {
        return ", min/avg/max latency: " +
                minLatency.get() / 1000L + "/" +
                cumulativeLatencyInterval.get() / (1000L * latencyCountInterval.get()) + "/" +
                maxLatency.get() / 1000L + " µs ";
    }

    private void writeToCsvIfNecessary(long now, double ratePublished, double rateReturned, double rateConfirmed, double rateNacked, double rateConsumed,
                long[] consumerLatencyStats, long[] confirmLatencyStats) {
        if (this.csvOut != null && !printFinalOnGoingOrDone.get()) {
            if (consumerLatencyStats == null) {
                consumerLatencyStats = getStats(latency);
            }
            if (confirmLatencyStats == null) {
                confirmLatencyStats = getStats(confirmLatency);
            }
            this.csvOut.println(testID + "," + format("%.3f", (now - startTime) / NANO_TO_SECOND) + "," +
                    rate(ratePublished, sendStatsEnabled) + "," +
                    rate(rateReturned, sendStatsEnabled && returnStatsEnabled) + "," +
                    rate(rateConfirmed, sendStatsEnabled && confirmStatsEnabled) + "," +
                    rate(rateNacked, sendStatsEnabled && confirmStatsEnabled) + "," +
                    rate(rateConsumed, recvStatsEnabled) + "," +
                    (shouldDisplayConsumerLatency() ?
                            consumerLatencyStats[0] + "," +
                                    consumerLatencyStats[1] + "," +
                                    consumerLatencyStats[2] + "," +
                                    consumerLatencyStats[3] + "," +
                                    consumerLatencyStats[4] + ","
                            : ",,,,,") +
                    (shouldDisplayConfirmLatency() ?
                            confirmLatencyStats[0] + "," +
                                    confirmLatencyStats[1] + "," +
                                    confirmLatencyStats[2] + "," +
                                    confirmLatencyStats[3] + "," +
                                    confirmLatencyStats[4]
                            : ",,,,")

            );
        }
    }

    boolean shouldDisplayConsumerLatency() {
        return recvStatsEnabled;
    }

    boolean shouldDisplayConfirmLatency() {
        return sendStatsEnabled && confirmStatsEnabled;
    }

    private long[] getStats(Histogram histogram) {
        return new long[]{
                div(histogram.getSnapshot().getMin()),
                div(histogram.getSnapshot().getMedian()),
                div(histogram.getSnapshot().get75thPercentile()),
                div(histogram.getSnapshot().get95thPercentile()),
                div(histogram.getSnapshot().get99thPercentile())
        };
    }

    private long div(double p) {
        if (useMillis) {
            return (long) p;
        } else {
            return (long) (p / 1000L);
        }
    }

    private String getRate(String descr, double rate, boolean display) {
        if (display) {
            return ", " + descr + ": " + formatRate(rate) + " " + MESSAGE_RATE_LABEL;
        } else {
            return "";
        }
    }

    public void printFinal() {
        if (printFinalOnGoingOrDone.compareAndSet(false, true)) {
            long now = System.nanoTime();
            long st = this.startTimeForGlobals.get();
            long elapsed = Duration.ofNanos(now - st).toMillis();
            String lineSeparator = System.getProperty("line.separator");
            StringBuilder summary = new StringBuilder("id: " + testID + ", sending rate avg: " +
                    formatRate(sendCountTotal.get() * MS_TO_SECOND / elapsed) +
                    " " + MESSAGE_RATE_LABEL);
            summary.append(lineSeparator);

            if (elapsed > 0) {
                summary.append("id: " + testID + ", receiving rate avg: " +
                        formatRate(recvCountTotal.get() * MS_TO_SECOND / elapsed) +
                        " " + MESSAGE_RATE_LABEL).append(lineSeparator);
                if (shouldDisplayConsumerLatency()) {
                    summary.append(format("id: %s, consumer latency %s %s",
                        testID, LATENCY_HEADER, latencyReport(this.globalLatency.get())
                    )).append(lineSeparator);
                }
                if (shouldDisplayConfirmLatency()) {
                    summary.append(format("id: %s, confirm latency %s %s",
                        testID, LATENCY_HEADER, latencyReport(this.globalConfirmLatency.get())
                    )).append(lineSeparator);
                }
                System.out.print(summary);
            }
        }
    }
}
