package org.sonatype.cs.metrics.service;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.cs.metrics.model.PayloadItem;
import org.sonatype.cs.metrics.util.DataLoaderParams;
import org.sonatype.cs.metrics.util.SqlStatements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Map;

@Service
public class LoaderService {

    private static final Logger log = LoggerFactory.getLogger(LoaderService.class);

    @Autowired private DbService dbService;

    @Autowired private PeriodsDataService periodsDataService;

    @Value("${metrics.dir}")
    private String metricsDir;

    @Value("${data.successmetrics}")
    private String successmetricsFile;

    @Value("${data.includelatestperiod}")
    private boolean includelatestperiod;

    @Value("${data.loadInsightsMetrics}")
    private boolean loadInsightsMetrics;

    public boolean successMetricsFileLoaded = false;
    public boolean applicationEvaluationsFileLoaded = false;
    public boolean policyViolationsDataLoaded = false;
    public boolean componentWaiversLoaded = false;
    public boolean autoreleasedFromQuarantineComponentsLoaded = false;
    public boolean quarantinedComponentsLoaded = false;

    public boolean loadAllMetrics() throws IOException, ParseException {

        successMetricsFileLoaded = loadSuccessMetricsData();

        applicationEvaluationsFileLoaded =
                this.loadMetricsFile(
                        DataLoaderParams.aeDatafile,
                        DataLoaderParams.aeFileHeader,
                        SqlStatements.ApplicationEvaluationsTable);
        policyViolationsDataLoaded =
                this.loadMetricsFile(
                        DataLoaderParams.pvDatafile,
                        DataLoaderParams.pvFileHeader,
                        SqlStatements.PolicyViolationsTables);

        componentWaiversLoaded =
                this.loadMetricsFile(
                        DataLoaderParams.cwDatafile,
                        DataLoaderParams.cwFileHeader,
                        SqlStatements.ComponentWaiversTable);
        quarantinedComponentsLoaded =
                this.loadMetricsFile(
                        DataLoaderParams.qcompDatafile,
                        DataLoaderParams.qcompHeader,
                        SqlStatements.QuarantinedComponentsTable);
        autoreleasedFromQuarantineComponentsLoaded =
                this.loadMetricsFile(
                        DataLoaderParams.afqcomponentDatafile,
                        DataLoaderParams.afqcomponentHeader,
                        SqlStatements.AutoreleasedFromQuarantinedComponentsTable);

        return successMetricsFileLoaded;
    }

    private boolean loadSuccessMetricsData() throws IOException, ParseException {
        String stmt = SqlStatements.MetricsTable;
        boolean fileLoaded = loadMetricsFile(successmetricsFile, DataLoaderParams.smHeader, stmt);
        boolean doAnalysis = false;

        if (fileLoaded) {
            Map<String, Object> periods =
                    periodsDataService.getPeriodData(SqlStatements.METRICTABLENAME);
            doAnalysis = (boolean) periods.get("doAnalysis");

            if (doAnalysis) {
                if (!includelatestperiod) {
                    String endPeriod = periods.get("endPeriod").toString();
                    filterOutLatestPeriod(endPeriod); // it is likely incomplete and only where we
                    // know multiple
                    // periods available
                    log.info("Removing incomplete data for current month " + endPeriod);
                }

                if (doAnalysis && loadInsightsMetrics) {
                    log.info("Loading insights data");
                    loadInsightsData();
                }
            }
        }

        return fileLoaded;
    }

    private void filterOutLatestPeriod(String endPeriod) throws ParseException {
        String sqlStmt = "delete from metric where time_period_start = " + "'" + endPeriod + "'";
        dbService.runSqlLoad(sqlStmt);
        return;
    }

    private void loadInsightsData() throws ParseException {
        Map<String, Object> periods =
                periodsDataService.getPeriodData(SqlStatements.METRICTABLENAME);

        String midPeriod = periods.get("midPeriod").toString();

        log.info("Mid period: " + midPeriod);

        String sqlStmtP1 =
                "DROP TABLE IF EXISTS METRIC_P1; CREATE TABLE METRIC_P1 AS SELECT * FROM METRIC"
                        + " WHERE TIME_PERIOD_START <= '"
                        + midPeriod
                        + "'";
        dbService.runSqlLoad(sqlStmtP1);

        String sqlStmtP2 =
                "DROP TABLE IF EXISTS METRIC_P2; CREATE TABLE METRIC_P2 AS SELECT * FROM METRIC"
                        + " WHERE TIME_PERIOD_START > '"
                        + midPeriod
                        + "'";
        dbService.runSqlLoad(sqlStmtP2);

        return;
    }

    private boolean loadMetricsFile(String fileName, String header, String stmt) throws IOException {
        boolean status = false;

        String filePath =
                Paths.get(System.getProperty("user.dir"))
                        .resolve(Paths.get(metricsDir).resolve(fileName))
                        .toString();

        log.info("Loading file: " + filePath);

        if (isHeaderValid(filePath, header)) {
            status = loadFile(filePath, stmt);
        }

        return status;
    }

    private static boolean isHeaderValid(String filename, String header) throws IOException {
        boolean isValid = false;
        String metricsFile = filename;
        File f = new File(metricsFile);

        log.info("Validating metrics file: " + metricsFile);

        if (f.exists()) {
            if (!f.isDirectory() && f.length() > 0) {
                isValid = true;

                if (header.length() > 0) {
                    String firstLine = getFirstLine(metricsFile);

                    if (!firstLine.startsWith(header)) {
                        log.error("Invalid header");
                        log.error("-> " + firstLine);
                        isValid = false;
                    } else {
                        if (countLines(metricsFile) < 2) {
                            log.warn("No metrics data in file");
                            isValid = false;
                        }
                    }
                }
            }
            else {
                log.info("No data");
                isValid = false;
            }
        }
        else {
            log.warn("File not found: " + metricsFile);
        }

        return isValid;
    }

    private boolean loadFile(String fileName, String stmt) throws IOException {
        String sqlStmt = stmt + " ('" + fileName + "')";
        dbService.runSqlLoad(sqlStmt);
        log.info("Loaded file: " + fileName);
        return true;
    }


    private static String getFirstLine(String fileName) throws FileNotFoundException, IOException {
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     new FileInputStream(fileName), StandardCharsets.ISO_8859_1))) {
            String line = br.readLine();
            return line;
        }
    }

    private static int countLines(String fileName) throws IOException {
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     new FileInputStream(fileName), StandardCharsets.ISO_8859_1))) {
            String line = br.readLine();
            int lineCount = 0;

            while (line != null) {
                lineCount++;
                line = br.readLine();
            }

            return lineCount;
        }
    }
}
