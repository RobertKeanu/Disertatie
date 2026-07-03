package org.unibuc.metrics;

import com.opencsv.CSVWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYErrorRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.unibuc.core.FinalLoadExperimentConfig;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public class LoadSweepResultsExporter {

    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 700;
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 33);
    private static final Font AXIS_LABEL_FONT = new Font("SansSerif", Font.BOLD, 26);
    private static final Font TICK_LABEL_FONT = new Font("SansSerif", Font.PLAIN, 22);
    private static final Font LEGEND_FONT = new Font("SansSerif", Font.PLAIN, 22);
    private static final Color[] SERIES_COLORS = {
            new Color(31, 119, 180),
            new Color(255, 127, 14),
            new Color(44, 160, 44),
            new Color(214, 39, 40),
            new Color(148, 103, 189),
            new Color(140, 86, 75),
            new Color(227, 119, 194),
            new Color(23, 190, 207)
    };

    private final String outputDir;

    public LoadSweepResultsExporter(String outputDir) {
        this.outputDir = outputDir;
    }

    public void export(List<LoadScenarioResult> results) throws IOException {
        export(results, List.of());
    }

    public void export(
            List<LoadScenarioResult> results,
            List<LoadExperimentFailure> failures) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        writeCsv(results);
        writeFailuresCsv(failures);
        writeMetricChart(
                results,
                "average_latency_vs_load.png",
                "Average Latency vs Load",
                "Average latency (s)",
                AggregatedMetrics::getAvgLatencyMean,
                AggregatedMetrics::getAvgLatencyStdDev
        );
        writeLatencyWithSelectionOverheadChart(results);
        writeMetricChart(
                results,
                "p99_latency_vs_load.png",
                "p99 Latency vs Load",
                "p99 latency (s)",
                AggregatedMetrics::getP99LatencyMean,
                AggregatedMetrics::getP99LatencyStdDev
        );
        writeMetricChart(
                results,
                "throughput_vs_load.png",
                "Throughput vs Load",
                "Requests/s",
                AggregatedMetrics::getThroughputMean,
                AggregatedMetrics::getThroughputStdDev
        );
        writeMetricChart(
                results,
                "queue_wait_vs_load.png",
                "Queue Wait vs Load",
                "Average queue wait (s)",
                AggregatedMetrics::getQueueWaitMean,
                AggregatedMetrics::getQueueWaitStdDev
        );
        writeMetricChart(
                results,
                "makespan_vs_load.png",
                "Makespan vs Load",
                "Makespan (s)",
                AggregatedMetrics::getMakespanMean,
                AggregatedMetrics::getMakespanStdDev
        );
    }

    private void writeFailuresCsv(List<LoadExperimentFailure> failures) throws IOException {
        Path path = Paths.get(outputDir, "load_sweep_failures.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
            writer.writeNext(new String[]{
                    "Load Level", "Target Load (%)", "Algorithm", "Run",
                    "Status", "Completed Requests", "Expected Requests",
                    "Wall Time (s)"
            });
            for (LoadExperimentFailure failure : failures) {
                writer.writeNext(new String[]{
                        failure.loadLabel(),
                        fmt(failure.loadPercentage()),
                        failure.algorithm(),
                        String.valueOf(failure.run()),
                        failure.status(),
                        String.valueOf(failure.completedRequests()),
                        String.valueOf(failure.expectedRequests()),
                        fmt(failure.wallTimeSeconds())
                });
            }
        }
    }

    private void writeCsv(List<LoadScenarioResult> results) throws IOException {
        Path path = Paths.get(outputDir, "load_sweep_summary.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
            writer.writeNext(new String[]{
                    "Load Level", "Target Load (%)", "Target Requests/s",
                    "Mean Interarrival Time (s)", "Algorithm", "Runs",
                    "Completed Requests Mean", "Completed Requests StdDev",
                    "Completed Requests Minimum",
                    "Makespan Mean (s)", "Makespan StdDev",
                    "Throughput Mean (req/s)", "Throughput StdDev",
                    "Average Latency Mean (s)", "Average Latency StdDev",
                    "Average Queue Wait Mean (s)", "Average Queue Wait StdDev",
                    "p95 Latency Mean (s)", "p95 Latency StdDev",
                    "p99 Latency Mean (s)", "p99 Latency StdDev",
                    "Load Imbalance Mean", "Load Imbalance StdDev",
                    "Request Imbalance Mean", "Request Imbalance StdDev",
                    "Raw Work Imbalance Mean", "Raw Work Imbalance StdDev"
            });

            for (LoadScenarioResult result : results) {
                AggregatedMetrics metrics = result.metrics();
                writer.writeNext(new String[]{
                        result.loadLabel(),
                        fmt(result.loadPercentage()),
                        fmt(result.targetRequestsPerSecond()),
                        fmt(result.meanInterarrivalTimeSeconds()),
                        metrics.getAlgorithmName(),
                        String.valueOf(metrics.getRuns()),
                        fmt(metrics.getCompletedRequestsMean()),
                        fmt(metrics.getCompletedRequestsStdDev()),
                        fmt(metrics.getCompletedRequestsMin()),
                        fmt(metrics.getMakespanMean()),
                        fmt(metrics.getMakespanStdDev()),
                        fmt(metrics.getThroughputMean()),
                        fmt(metrics.getThroughputStdDev()),
                        fmt(metrics.getAvgLatencyMean()),
                        fmt(metrics.getAvgLatencyStdDev()),
                        fmt(metrics.getQueueWaitMean()),
                        fmt(metrics.getQueueWaitStdDev()),
                        fmt(metrics.getP95LatencyMean()),
                        fmt(metrics.getP95LatencyStdDev()),
                        fmt(metrics.getP99LatencyMean()),
                        fmt(metrics.getP99LatencyStdDev()),
                        fmt(metrics.getImbalanceMean()),
                        fmt(metrics.getImbalanceStdDev()),
                        fmt(metrics.getRequestImbalanceMean()),
                        fmt(metrics.getRequestImbalanceStdDev()),
                        fmt(metrics.getWorkImbalanceMean()),
                        fmt(metrics.getWorkImbalanceStdDev())
                });
            }
        }
    }

    private void writeMetricChart(
            List<LoadScenarioResult> results,
            String filename,
            String title,
            String yAxisLabel,
            ToDoubleFunction<AggregatedMetrics> meanExtractor,
            ToDoubleFunction<AggregatedMetrics> stdDevExtractor) throws IOException {

        YIntervalSeriesCollection dataset = buildDataset(
                results,
                result -> meanExtractor.applyAsDouble(result.metrics()),
                stdDevExtractor
        );
        writeLineChart(filename, title, yAxisLabel, dataset);
    }

    private void writeLatencyWithSelectionOverheadChart(
            List<LoadScenarioResult> results) throws IOException {
        YIntervalSeriesCollection dataset = buildDataset(
                results,
                this::latencyWithSelectionOverhead,
                AggregatedMetrics::getAvgLatencyStdDev
        );
        writeLineChart(
                "average_latency_with_selection_overhead_vs_load.png",
                "Latency with overhead",
                "Latency (s)",
                dataset
        );
    }

    private YIntervalSeriesCollection buildDataset(
            List<LoadScenarioResult> results,
            ToDoubleFunction<LoadScenarioResult> meanExtractor,
            ToDoubleFunction<AggregatedMetrics> stdDevExtractor) {
        YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();
        Map<String, List<LoadScenarioResult>> byAlgorithm =
                groupByAlgorithm(results);

        for (Map.Entry<String, List<LoadScenarioResult>> entry
                : byAlgorithm.entrySet()) {
            String algorithmName = entry.getKey();
            String shortName = shortenName(algorithmName);
            YIntervalSeries series = new YIntervalSeries(shortName);
            List<LoadScenarioResult> orderedResults =
                    new ArrayList<>(entry.getValue());
            orderedResults.sort(
                    Comparator.comparingDouble(
                            LoadScenarioResult::loadPercentage
                    )
            );

            for (LoadScenarioResult result : orderedResults) {
                double mean = meanExtractor.applyAsDouble(result);
                double stdDev = stdDevExtractor.applyAsDouble(result.metrics());
                series.add(
                        result.loadPercentage(),
                        mean,
                        Math.max(0, mean - stdDev),
                        mean + stdDev
                );
            }

            dataset.addSeries(series);
        }
        return dataset;
    }

    private double latencyWithSelectionOverhead(LoadScenarioResult result) {
        return result.metrics().getAvgLatencyMean()
                + FinalLoadExperimentConfig.estimatedSelectionServiceTimeSeconds(
                        result.metrics().getAlgorithmName()
                );
    }

    private void writeLineChart(
            String filename,
            String title,
            String yAxisLabel,
            YIntervalSeriesCollection dataset) throws IOException {
        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "Target load (%)",
                yAxisLabel,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        chart.setBackgroundPaint(Color.WHITE);
        XYPlot plot = chart.getXYPlot();
        configurePlot(plot);

        XYErrorRenderer renderer =
                createMetricRenderer(dataset, true);
        plot.setRenderer(renderer);

        NumberAxis loadAxis = (NumberAxis) plot.getDomainAxis();
        loadAxis.setRange(20, 115);
        loadAxis.setTickUnit(new NumberTickUnit(10));
        loadAxis.setLabelFont(AXIS_LABEL_FONT);
        loadAxis.setTickLabelFont(TICK_LABEL_FONT);

        NumberAxis latencyAxis = (NumberAxis) plot.getRangeAxis();
        configureRangeAxis(latencyAxis);
        chart.getTitle().setFont(TITLE_FONT);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(LEGEND_FONT);
        }

        saveChartWithRetry(chart, filename);
    }

    private XYErrorRenderer createMetricRenderer(
            YIntervalSeriesCollection dataset,
            boolean drawErrors) {
        XYErrorRenderer renderer = new XYErrorRenderer();
        renderer.setDrawXError(false);
        renderer.setDrawYError(drawErrors);
        renderer.setDefaultLinesVisible(true);
        renderer.setDefaultShapesVisible(true);
        renderer.setCapLength(5.0);
        renderer.setErrorPaint(Color.DARK_GRAY);
        renderer.setErrorStroke(new BasicStroke(1.0f));

        for (int seriesIndex = 0;
             seriesIndex < dataset.getSeriesCount();
             seriesIndex++) {
            Color color = SERIES_COLORS[seriesIndex % SERIES_COLORS.length];
            renderer.setSeriesPaint(seriesIndex, color);
            renderer.setSeriesStroke(seriesIndex, new BasicStroke(2.0f));
        }
        return renderer;
    }

    private void configurePlot(XYPlot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(220, 220, 220));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
    }

    private void configureRangeAxis(NumberAxis axis) {
        axis.setAutoRangeIncludesZero(true);
        axis.setLabelFont(AXIS_LABEL_FONT);
        axis.setTickLabelFont(TICK_LABEL_FONT);
    }

    private void saveChartWithRetry(
            JFreeChart chart,
            String filename) throws IOException {
        File target = Paths.get(outputDir, filename).toFile();
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                ChartUtils.saveChartAsPNG(target, chart, CHART_WIDTH, CHART_HEIGHT);
                return;
            } catch (IOException e) {
                lastFailure = e;
                sleepBeforeRetry(attempt);
            }
        }

        throw lastFailure;
    }

    private void sleepBeforeRetry(int attempt) throws IOException {
        try {
            Thread.sleep(150L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retrying chart export", e);
        }
    }

    private Map<String, List<LoadScenarioResult>> groupByAlgorithm(
            List<LoadScenarioResult> results) {
        Map<String, List<LoadScenarioResult>> grouped = new LinkedHashMap<>();
        for (LoadScenarioResult result : results) {
            grouped.computeIfAbsent(
                    result.metrics().getAlgorithmName(),
                    key -> new ArrayList<>()
            ).add(result);
        }
        return grouped;
    }

    private String shortenName(String name) {
        return name
                .replace("Adaptive Weighted Round Robin", "Adaptive WRR")
                .replace("Power of Two Choices", "P2C")
                .replace("Least Response Time", "LRT")
                .replace("Least Connections", "LC")
                .replace("Round Robin", "RR")
                .replace("P2C-LRT Hybrid", "P2C-LRT");
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
