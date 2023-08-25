// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.nats.tuning.consumercreate;

import io.nats.client.*;
import io.nats.client.api.StreamConfiguration;
import io.nats.tuning.support.UniqueSubjectGenerator;
import io.nats.tuning.support.Utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.nats.tuning.consumercreate.Report.writeCsv;
import static io.nats.tuning.consumercreate.Report.writeTextReport;

/*
    Code to help tune Consumer Create on startup
 */
public class Main {

    public static void main(String[] args) throws Exception {
        List<Report> reports = new ArrayList<>();
        Settings settings = new Settings();

        settings.optionsBuilder = () -> Options.builder().server("localhost:4222,localhost:5222,localhost:6222");

        int[] threadsPerApp = new int[]{100};
        AppStrategy[] appStrategies = new AppStrategy[]{AppStrategy.Client_Api_Subscribe}; // , AppStrategy.Individual_Immediately, AppStrategy.Individual_After_Creates};
        SubStrategy[] subStrategies = new SubStrategy[]{SubStrategy.Pull_Provide_Stream}; // SubStrategy.values();

        for (AppStrategy asy : appStrategies) {
            for (SubStrategy ssy : subStrategies) {
                for (int tpa : threadsPerApp) {
                    Thread.sleep(1000);
                    String title = tpa + " " + asy.name().toLowerCase().replace("_", " ");
                    settings.streamName = title.replace(" ", "-");
                    settings.subjectGenerator = new UniqueSubjectGenerator();
                    settings.threadsPerApp = tpa;
                    settings.appStrategy = asy;
                    settings.subStrategy = ssy;
                    settings.timeoutMs = 180_000;
//                    settings.autoReportFrequency();

                    if (settings.isValid()) { // just skip invalid settings when strategies don't work together.
                        Report r = run(title, settings);
                        if (r != null) {
                            reports.add(r);
                        }
                        cleanupAfterRun(settings);
                    }
                }
            }
        }

        writeTextReport(reports, "C:\\temp\\create-consumer-report.txt");
        writeCsv(reports, "C:\\temp\\create-consumer-report.csv");
    }

    private static void cleanupAfterRun(Settings settings) {
        if (settings.cleanupAfterRun) {
            try (Connection nc = Nats.connect(settings.optionsBuilder.getBuilder().build())) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                jsm.deleteStream(settings.streamName);
            }
            catch (Exception ignore) {}
        }
    }

    public static Report run(String title, Settings settings) {
        settings.validate();

        try (Connection nc = Nats.connect(settings.optionsBuilder.getBuilder().build())) {
            if (settings.verifyConnectMs > 0) {
                if (!Utils.waitForStatus(nc, settings.verifyConnectMs, Connection.Status.CONNECTED)) {
                    throw new RuntimeException("Connection not established within verify time of " + settings.verifyConnectMs + "ms");
                }
            }

            JetStreamOptions jso = JetStreamOptions.builder().requestTimeout(Duration.ofMillis(settings.timeoutMs)).build();
            JetStreamManagement jsm = nc.jetStreamManagement(jso);
            JetStream js = nc.jetStream(jso);

            // set up the stream
            try { jsm.deleteStream(settings.streamName); } catch (Exception ignore) {}
            jsm.addStream(StreamConfiguration.builder()
                .name(settings.streamName)
                .storageType(settings.storageType)
                .subjects(settings.subjectGenerator.getStreamSubject())
                .replicas(settings.replicas)
                .build());

            // start publishing - this provides load and message for subscriptions
            Publisher[] publishers = new Publisher[settings.publishInstances];
            for (int x = 0; x < settings.publishInstances; x++) {
                publishers[x] = new Publisher(settings, js, x);
                publishers[x].start();
            }
            Thread.sleep(settings.pauseAfterStartPublishingMs);

            long start = System.nanoTime();

            AppSimulator[] apps = new AppSimulator[settings.appInstances];
            for (int appId = 0; appId < settings.appInstances; appId++) {
                apps[appId] = new AppSimulator(settings, appId);
                apps[appId].start();
            }

            for (int appId = 0; appId < settings.appInstances; appId++) {
                apps[appId].join();
            }

            long elapsed = System.nanoTime() - start;

            for (Publisher p : publishers) {
                p.go.set(false);
            }
            for (Publisher p : publishers) {
                p.join();
            }

            Report r = new Report(title, settings, apps, elapsed);
            r.print(System.out);
            return r;
        }
        catch (Exception e) {
            System.err.println("MAIN RUN EX");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return null;
        }
    }
}
