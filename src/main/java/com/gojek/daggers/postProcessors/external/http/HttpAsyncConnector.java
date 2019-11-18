package com.gojek.daggers.postProcessors.external.http;

import com.gojek.daggers.exception.InvalidConfigurationException;
import com.gojek.daggers.metrics.ExternalSourceAspects;
import com.gojek.daggers.metrics.StatsManager;
import com.gojek.daggers.postProcessors.common.ColumnNameManager;
import com.gojek.daggers.postProcessors.external.common.RowManager;
import com.gojek.de.stencil.StencilClient;
import com.google.protobuf.Descriptors;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.types.Row;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.UnknownFormatConversionException;
import java.util.concurrent.TimeoutException;

import static com.gojek.daggers.metrics.ExternalSourceAspects.*;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public class HttpAsyncConnector extends RichAsyncFunction<Row, Row> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAsyncConnector.class.getName());
    private AsyncHttpClient httpClient;
    private HttpSourceConfig httpSourceConfig;
    private StencilClient stencilClient;
    private ColumnNameManager columnNameManager;
    private Descriptors.Descriptor outputDescriptor;
    private StatsManager statsManager;


    public HttpAsyncConnector(HttpSourceConfig httpSourceConfig, StencilClient stencilClient, ColumnNameManager columnNameManager) {
        this.httpSourceConfig = httpSourceConfig;
        this.stencilClient = stencilClient;
        this.columnNameManager = columnNameManager;
    }

    public HttpAsyncConnector(HttpSourceConfig httpSourceConfig, StencilClient stencilClient, AsyncHttpClient httpClient, StatsManager statsManager, ColumnNameManager columnNameManager) {
        this(httpSourceConfig, stencilClient, columnNameManager);
        this.httpClient = httpClient;
        this.statsManager = statsManager;
    }

    @Override
    public void open(Configuration configuration) throws Exception {
        super.open(configuration);

        if (httpSourceConfig.getType() != null) {
            String descriptorType = httpSourceConfig.getType();
            outputDescriptor = stencilClient.get(descriptorType);
        }
        if (statsManager == null) {
            statsManager = new StatsManager(getRuntimeContext(), true);
        }
        statsManager.register("external.source.http", ExternalSourceAspects.values());
        if (httpClient == null) {
            httpClient = asyncHttpClient(config().setConnectTimeout(httpSourceConfig.getConnectTimeout()));
        }
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
        statsManager.markEvent(CLOSE_CONNECTION_ON_HTTP_CLIENT);
        LOGGER.error("HTTP Connector : Connection closed");
    }

    @Override
    public void asyncInvoke(Row input, ResultFuture<Row> resultFuture) throws Exception {
        try {
            RowManager rowManager = new RowManager(input);
            Object[] bodyVariables = getBodyVariablesValues(rowManager, resultFuture);
            if (StringUtils.isEmpty(httpSourceConfig.getRequestPattern()) || Arrays.asList(bodyVariables).isEmpty()) {
                resultFuture.complete(Collections.singleton(rowManager.getAll()));
                statsManager.markEvent(EMPTY_INPUT);
                return;
            }
            String requestBody = String.format(httpSourceConfig.getRequestPattern(), bodyVariables);
            String endpoint = httpSourceConfig.getEndpoint();
            BoundRequestBuilder postRequest = httpClient
                    .preparePost(endpoint)
                    .setBody(requestBody);
            addCustomHeaders(postRequest);

            HttpResponseHandler httpResponseHandler = new HttpResponseHandler(httpSourceConfig, statsManager, rowManager, columnNameManager, outputDescriptor, resultFuture);
            statsManager.markEvent(ExternalSourceAspects.TOTAL_HTTP_CALLS);
            httpResponseHandler.startTimer();
            postRequest.execute(httpResponseHandler);
        } catch (UnknownFormatConversionException e) {
            statsManager.markEvent(ExternalSourceAspects.INVALID_CONFIGURATION);
            resultFuture.completeExceptionally(new InvalidConfigurationException(String.format("Request pattern '%s' is invalid", httpSourceConfig.getRequestPattern())));
        } catch (IllegalFormatException e) {
            statsManager.markEvent(INVALID_CONFIGURATION);
            resultFuture.completeExceptionally(new InvalidConfigurationException(String.format("Request pattern '%s' is incompatible with variable", httpSourceConfig.getRequestPattern())));
        }

    }

    public void timeout(Row input, ResultFuture<Row> resultFuture) throws Exception {
        RowManager rowManager = new RowManager(input);
        statsManager.markEvent(ExternalSourceAspects.TIMEOUTS);
        LOGGER.error("HTTP Connector : Timeout");
        if (httpSourceConfig.isFailOnErrors())
            resultFuture.completeExceptionally(new TimeoutException("Timeout in HTTP Call"));
        resultFuture.complete(Collections.singleton(rowManager.getAll()));
    }

    private Object[] getBodyVariablesValues(RowManager rowManager, ResultFuture<Row> resultFuture) {
        List<String> requiredInputColumns = Arrays.asList(httpSourceConfig.getRequestVariables().split(","));
        ArrayList<Object> inputColumnValues = new ArrayList<>();
        for (String inputColumnName : requiredInputColumns) {
            int inputColumnIndex = columnNameManager.getInputIndex(inputColumnName);
            if (inputColumnIndex == -1) {
                statsManager.markEvent(INVALID_CONFIGURATION);
                resultFuture.completeExceptionally(new InvalidConfigurationException(String.format("Column '%s' not found as configured in the request variable", inputColumnName)));
                return new Object[0];
            }
            inputColumnValues.add(rowManager.getFromInput(inputColumnIndex));
        }
        requiredInputColumns.forEach(inputColumnName -> {

        });
        return inputColumnValues.toArray();
    }

    private void addCustomHeaders(BoundRequestBuilder postRequest) {
        Map<String, String> headerMap;
        headerMap = httpSourceConfig.getHeaders();
        headerMap.keySet().forEach(headerKey -> {
            postRequest.addHeader(headerKey, headerMap.get(headerKey));
        });
    }


}