package org.unibuc.metrics;

import com.opencsv.CSVWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class ResultsExporter {

    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 720;
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 30);
    private static final Font AXIS_LABEL_FONT = new Font("SansSerif", Font.BOLD, 26);
    private static final Font TICK_LABEL_FONT = new Font("SansSerif", Font.PLAIN, 22);
    private static final Font LEGEND_FONT = new Font("SansSerif", Font.PLAIN, 22);
    private static final Color AVG_LATENCY_COLOR = new Color(76, 120, 168);
    private static final Color QUEUE_WAIT_COLOR = new Color(245, 133, 24);
    private static final Color P95_LATENCY_COLOR = new Color(84, 162, 75);
    private static final Color P99_LATENCY_COLOR = new Color(228, 87, 86);
    private static final Color THROUGHPUT_COLOR = new Color(0, 158, 150);
    private static final Color LOAD_IMBALANCE_COLOR = new Color(128, 100, 162);
    private static final Color WORK_IMBALANCE_COLOR = new Color(156, 117, 95);
    private static final Color MAKESPAN_COLOR = new Color(218, 165, 32);

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
                    "Load Imbalance ",
                    "Request Imbalance", "Raw Work Imbalance "
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
        saveChart(styledBarChart("", "Algorithm", "Time (s)", dataset),
                "latency_chart.png", CHART_WIDTH, CHART_HEIGHT);
    }

    private void writeThroughputChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getThroughput(), "Throughput", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("","Algorithm", "Requests/s", dataset),
                "throughput_chart.png", CHART_WIDTH, CHART_HEIGHT);
    }

    private void writeImbalanceChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getNormalizedLoadImbalance(), "Load Imbalance", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("", "Algorithm", "Service-Time Std Dev (s)", dataset),
                "imbalance_chart.png", CHART_WIDTH, CHART_HEIGHT);
    }

    private void writeWorkImbalanceChart(List<MetricsCollector> results) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MetricsCollector m : results) {
            dataset.addValue(m.getWorkImbalance(), "Work Imbalance", shortenName(m.getAlgorithmName()));
        }
        saveChart(styledBarChart("", "Algorithm", "MI Std Dev", dataset),
                "work_imbalance_chart.png", CHART_WIDTH, CHART_HEIGHT);
    }

    public void exportAggregated(List<AggregatedMetrics> results) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        writeAggregatedCsv(results);
        writeStatisticalChart(results, "latency_agg_chart.png",    "",    "Time (s)");
        writeStatisticalChart(results, "throughput_agg_chart.png", "", "Requests/s");
        writeStatisticalChart(results, "imbalance_agg_chart.png",  "",        "Load Imbalance ");
        writeStatisticalChart(results, "work_imbalance_agg_chart.png", "",     "Work Imbalance ");
        writeStatisticalChart(results, "makespan_agg_chart.png",   "",   "Seconds");
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
                    "Load Imbalance Mean", "Load Imbalance StdDev",
                    "Request Imbalance Mean ", "Request Imbalance StdDev",
                    "Raw Work Imbalance Mean", "Raw Work Imbalance StdDev"
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
                dataset.add(a.getWorkImbalanceMean(), a.getWorkImbalanceStdDev(), "Work Imbalance", name);
            } else if (normalizedLabel.contains("imbalance") || yLabel.contains("σ")) {
                dataset.add(a.getImbalanceMean(), a.getImbalanceStdDev(), "Request Imbalance", name);
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
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setErrorIndicatorPaint(Color.DARK_GRAY);
        renderer.setErrorIndicatorStroke(new BasicStroke(1.5f));
        applyMetricColors(renderer, dataset);
        plot.setRenderer(renderer);

        plot.getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.UP_45
        );

        applyChartFonts(chart);
        saveChart(chart, filename, CHART_WIDTH, CHART_HEIGHT);
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

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        applyMetricColors(renderer, dataset);

        plot.getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.UP_45
        );
        applyChartFonts(chart);
        return chart;
    }

    private void applyChartFonts(JFreeChart chart) {
        chart.getTitle().setFont(TITLE_FONT);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(LEGEND_FONT);
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.getDomainAxis().setLabelFont(AXIS_LABEL_FONT);
        plot.getDomainAxis().setTickLabelFont(TICK_LABEL_FONT);
        plot.getRangeAxis().setLabelFont(AXIS_LABEL_FONT);
        plot.getRangeAxis().setTickLabelFont(TICK_LABEL_FONT);
    }

    private void applyMetricColors(BarRenderer renderer, CategoryDataset dataset) {
        for (int seriesIndex = 0; seriesIndex < dataset.getRowCount(); seriesIndex++) {
            String metric = dataset.getRowKey(seriesIndex).toString();
            renderer.setSeriesPaint(seriesIndex, colorForMetric(metric));
        }
    }

    private Color colorForMetric(String metric) {
        String normalized = metric.toLowerCase();
        if (normalized.contains("p99")) {
            return P99_LATENCY_COLOR;
        }
        if (normalized.contains("p95")) {
            return P95_LATENCY_COLOR;
        }
        if (normalized.contains("wait")) {
            return QUEUE_WAIT_COLOR;
        }
        if (normalized.equals("avg") || normalized.contains("avg latency")) {
            return AVG_LATENCY_COLOR;
        }
        if (normalized.contains("throughput")) {
            return THROUGHPUT_COLOR;
        }
        if (normalized.contains("work imbalance")) {
            return WORK_IMBALANCE_COLOR;
        }
        if (normalized.contains("imbalance")) {
            return LOAD_IMBALANCE_COLOR;
        }
        if (normalized.contains("makespan")) {
            return MAKESPAN_COLOR;
        }
        return AVG_LATENCY_COLOR;
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
        saveChart(styledBarChart("", "Algorithm", "Seconds", dataset),
                "makespan_chart.png", CHART_WIDTH, CHART_HEIGHT);
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
