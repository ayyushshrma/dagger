package io.odpf.dagger.functions.udfs.aggregate;

import io.odpf.dagger.common.udfs.AggregateUdf;
import io.odpf.dagger.functions.exceptions.OddNumberOfArgumentsException;
import io.odpf.dagger.functions.udfs.aggregate.accumulator.FeatureAccumulator;
import org.apache.flink.types.Row;

/**
 * User-defined aggregate function to get Features.
 */
public class Features extends AggregateUdf<Row[], FeatureAccumulator> {

    @Override
    public FeatureAccumulator createAccumulator() {
        return new FeatureAccumulator();
    }

    @Override
    public Row[] getValue(FeatureAccumulator featureAccumulator) {
        return featureAccumulator.getFeatures();
    }

    /**
     * Converts the given list of objects to a FeatureRow type to store in feast(https://github.com/feast-dev/feast)
     * with key and values from every even pairs passed in args.
     *
     * @param featureAccumulator the feature accumulator
     * @param objects            the objects as arguments
     * @return features the output in FeatureRow for every even pairs
     * @author zhilingc
     * @team DS
     */
    public void accumulate(FeatureAccumulator featureAccumulator, Object... objects) {
        if (objects.length % 2 != 0) {
            throw new OddNumberOfArgumentsException();
        }
        for (int elementIndex = 0; elementIndex < objects.length; elementIndex += 2) {
            featureAccumulator.add(String.valueOf(objects[elementIndex]), objects[elementIndex + 1]);
        }
    }
}
