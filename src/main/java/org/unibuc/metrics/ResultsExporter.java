package org.unibuc.metrics;

import com.opencsv.CSVWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class ResultsExporter {

    private final String outputDir;

    public ResultsExporter(String outputDir) {
        this.outputDir = outputDir;
    }

    public void export(List<MetricsCollector> results) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        writeCsv(results);
        writeLatencyChart(results);
        writeThroughputChart(results);
        writeImbalanceChart(results);
        writeWorkImbalanceChart(results);
        writeMakespanChart(results);
        System.out.println("Per-run results written to: " + Paths.get(outputDir).toAbsolutePath());
    }

    private void writeCsv(List<MetricsCollector> results) throws IOException {
        Path path = Paths.get(outputDir, "summary.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
            writer.writeNext(new String[]{
                    "Algorithm", "Total Requests", "Makespan (s)",
                    "Throughput (req/s)", "Avg Latency (s)",
                    "Avg Queue Wait (s)", "p95 Latency (s)", "p99 Latency (s)",
                    "Load Imbalance (service-time σ)",
                    "Request Imbalance (σ)", "Raw Work Imbalance (MI σ)"
            });
            for (MetricsCollector m : results) {
                writer.writeNext(new String[]{
                        m.getAlgorithmName(),
                        String.valueOf(m.getTotalRequests()),
                        fmt(m.getMakespan()),
                        fmt(m.getThroughput()),
                        fmt(m.getAverageLatency()),
                        fmt(m.getAverageQueueWaitTime()),
                        fmt(m.getP95Latency()),
                        fmt(m.getP99Latency()),
                        fmt(m.getNormalizedLoadImbalance()),
                        fmt(m.getRequestImbalance()),
                        fmt(m.getWorkImbalance())
                });
            }
        }
    }

    private void writeLatencyChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            String name = shortenName(m.getAlgorithmName());
            dataset.addValue(m.getAverageLatency(), "Avg Latency", name);
            dataset.addValue(m.getAverageQueueWaitTime(), "Avg Wait", name);
            dataset.addValue(m.getP95Latency(),     "p95 Latency", name);
            dataset.addValue(m.getP99Latency(),     "p99 Latency", name);
        }
        saveChart(styledBarChart("Latency Comparison", "Algorithm", "Time (s)", dataset),
                "latency_chart.png", 1000, 600);
    }

    private void writeThroughputChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getThroughput(), "Throughput", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("Throughput Comparison", "Algorithm", "Requests/s", dataset),
                "throughput_chart.png", 1000, 600);
    }

    private void writeImbalanceChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getNormalizedLoadImbalance(), "Load Imbalance σ", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("Load Imbalance (lower = better)", "Algorithm", "Service-Time Std Dev (s)", dataset),
                "imbalance_chart.png", 1000, 600);
    }

    private void writeWorkImbalanceChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getWorkImbalance(), "Work Imbalance σ", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("Work Imbalance (lower = better)", "Algorithm", "MI Std Dev", dataset),
                "work_imbalance_chart.png", 1000, 600);
    }

    public void exportAggregated(List<AggregatedMetrics> results) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        writeAggregatedCsv(results);
        writeStatisticalChart(results, "latency_agg_chart.png",    "Latency Comparison (mean ± σ)",    "Time (s)");
        writeStatisticalChart(results, "throughput_agg_chart.png", "Throughput Comparison (mean ± σ)", "Requests/s");
        writeStatisticalChart(results, "imbalance_agg_chart.png",  "Load Imbalance (mean ± σ)",        "Load Imbalance σ");
        writeStatisticalChart(results, "work_imbalance_agg_chart.png", "Work Imbalance (mean ± σ)",     "Work Imbalance (MI σ)");
        writeStatisticalChart(results, "makespan_agg_chart.png",   "Makespan Comparison (mean ± σ)",   "Seconds");
        System.out.println("Aggregated results written to: " + Paths.get(outputDir).toAbsolutePath());
    }

    private void writeAggregatedCsv(List<AggregatedMetrics> results) throws IOException {
        Path path = Paths.get(outputDir, "aggregated_summary.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
            writer.writeNext(new String[]{
                    "Algorithm", "Runs",
                    "Makespan Mean (s)", "Makespan StdDev",
                    "Throughput Mean (req/s)", "Throughput StdDev",
                    "Avg Latency Mean (s)", "Avg Latency StdDev",
                    "Avg Queue Wait Mean (s)", "Avg Queue Wait StdDev",
                    "p95 Latency Mean (s)", "p95 Latency StdDev",
                    "p99 Latency Mean (s)", "p99 Latency StdDev",
                    "Load Imbalance Mean (service-time σ)", "Load Imbalance StdDev",
                    "Request Imbalance Mean (σ)", "Request Imbalance StdDev",
                    "Raw Work Imbalance Mean (MI σ)", "Raw Work Imbalance StdDev"
            });
            for (AggregatedMetrics a : results) {
                writer.writeNext(new String[]{
                        a.getAlgorithmName(),
                        String.valueOf(a.getRuns()),
                        fmt(a.getMakespanMean()),    fmt(a.getMakespanStdDev()),
                        fmt(a.getThroughputMean()),  fmt(a.getThroughputStdDev()),
                        fmt(a.getAvgLatencyMean()),  fmt(a.getAvgLatencyStdDev()),
                        fmt(a.getQueueWaitMean()),    fmt(a.getQueueWaitStdDev()),
                        fmt(a.getP95LatencyMean()),  fmt(a.getP95LatencyStdDev()),
                        fmt(a.getP99LatencyMean()),  fmt(a.getP99LatencyStdDev()),
                        fmt(a.getImbalanceMean()),   fmt(a.getImbalanceStdDev()),
                        fmt(a.getRequestImbalanceMean()), fmt(a.getRequestImbalanceStdDev()),
                        fmt(a.getWorkImbalanceMean()), fmt(a.getWorkImbalanceStdDev())
                });
            }
        }
    }

    private void writeStatisticalChart(List<AggregatedMetrics> results,
                                       String filename, String title, String yLabel)
            throws IOException {

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();

        for (AggregatedMetrics a : results) {
            String name = shortenName(a.getAlgorithmName());
            String normalizedLabel = yLabel.toLowerCase();
            if (normalizedLabel.contains("time")) {
                dataset.add(a.getAvgLatencyMean(), a.getAvgLatencyStdDev(), "Avg", name);
                dataset.add(a.getQueueWaitMean(), a.getQueueWaitStdDev(), "Wait", name);
                dataset.add(a.getP95LatencyMean(), a.getP95LatencyStdDev(), "p95", name);
                dataset.add(a.getP99LatencyMean(), a.getP99LatencyStdDev(), "p99", name);
            } else if (normalizedLabel.contains("work")) {
                dataset.add(a.getWorkImbalanceMean(), a.getWorkImbalanceStdDev(), "Work Imbalance σ", name);
            } else if (normalizedLabel.contains("imbalance") || yLabel.contains("σ")) {
                dataset.add(a.getImbalanceMean(), a.getImbalanceStdDev(), "Request Imbalance σ", name);
            } else if (normalizedLabel.contains("requests/s") || normalizedLabel.contains("throughput")) {
                dataset.add(a.getThroughputMean(), a.getThroughputStdDev(), "Throughput", name);
            } else if (normalizedLabel.contains("seconds")) {
                dataset.add(a.getMakespanMean(), a.getMakespanStdDev(), "Makespan", name);
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title, "Algorithm", yLabel, dataset,
                PlotOrientation.VERTICAL, true, true, false
        );

        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(220, 220, 220));

        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        renderer.setErrorIndicatorPaint(Color.DARK_GRAY);
        renderer.setErrorIndicatorStroke(new BasicStroke(1.5f));
        plot.setRenderer(renderer);

        plot.getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.UP_45
        );

        saveChart(chart, filename, 1000, 600);
    }

    private JFreeChart styledBarChart(String title, String xLabel, String yLabel,
                                      DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart(
                title, xLabel, yLabel, dataset,
                PlotOrientation.VERTICAL, true, true, false
        );
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        plot.getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.UP_45
        );
        return chart;
    }

    private void saveChart(JFreeChart chart, String filename, int width, int height)
            throws IOException {
        File file = Paths.get(outputDir, filename).toFile();
        ChartUtils.saveChartAsPNG(file, chart, width, height);
    }
    private void writeMakespanChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getMakespan(), "Makespan", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("Makespan Comparison", "Algorithm", "Seconds", dataset),
                "makespan_chart.png", 1000, 600);
    }

    private String shortenName(String name) {
        return name
                .replace("Dynamic Weighted Least Connections", "DWLC")
                .replace("Consistent Hashing (Ring Hash)",     "Ring Hash")
                .replace("Power of Two Choices",               "P2C")
                .replace("Least Response Time",                "LRT")
                .replace("Predictive Round Robin",             "PRR")
                .replace("Weighted Round Robin",               "WRR")
                .replace("Least Connections",                  "LC")
                .replace("Round Robin",                        "RR")
                .replace("Maglev Hashing",                     "Maglev")
                .replace("RL Load Balancer (Q-Learning)",      "RL-LB")
                .replace("P2C-LRT Hybrid",                     "P2C-LRT");
    }

    private String fmt(double v) { return String.format("%.4f", v); }
}
