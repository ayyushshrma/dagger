package com.gojek.daggers.postProcessors.internal;

import com.gojek.daggers.postProcessors.common.ColumnNameManager;
import com.gojek.daggers.postProcessors.external.common.RowManager;
import com.gojek.daggers.postProcessors.internal.processor.InternalConfigProcessor;
import org.apache.flink.types.Row;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InternalDecoratorTest {

    private ColumnNameManager columnNameManager;

//    @Before
//    public void setup() {
//
//    }

    @Test
    public void canDecorateWhenInternalConfigIsPresent() {
        columnNameManager = new ColumnNameManager(new String[]{}, Arrays.asList());
        InternalDecorator internalDecorator = new InternalDecorator(mock(InternalSourceConfig.class), null, columnNameManager);

        Assert.assertTrue(internalDecorator.canDecorate());
    }

    @Test
    public void canNotDecorateWhenInternalConfigIsNotPresent() {
        columnNameManager = new ColumnNameManager(new String[]{}, Arrays.asList());
        InternalDecorator internalDecorator = new InternalDecorator(null, null, null);

        Assert.assertFalse(internalDecorator.canDecorate());
    }

    @Test
    public void shouldMapUsingProcessor() {
        columnNameManager = new ColumnNameManager(new String[]{}, Arrays.asList());
        InternalConfigProcessor processorMock = mock(InternalConfigProcessor.class);
        InternalDecorator internalDecorator = new InternalDecorator(null, processorMock, columnNameManager);
        Row dataStreamRow = new Row(2);

        internalDecorator.map(dataStreamRow);

        verify(processorMock).process(new RowManager(dataStreamRow));
    }

    @Test
    public void shouldUpdateRowWhenOutputRowSizeIsNotEqualToColumnSize() {
        columnNameManager = new ColumnNameManager(new String[]{}, Arrays.asList("output1", "output2"));
        Row dataStreamRow = new Row(2);
        dataStreamRow.setField(1, new Row(1));
        InternalConfigProcessor processorMock = mock(InternalConfigProcessor.class);
        InternalDecorator internalDecorator = new InternalDecorator(null, processorMock, columnNameManager);

        internalDecorator.map(dataStreamRow);
        Row outputRow = (Row) dataStreamRow.getField(1);

        Assert.assertEquals(columnNameManager.getOutputSize(), outputRow.getArity());
        verify(processorMock).process(new RowManager(dataStreamRow));
    }
}