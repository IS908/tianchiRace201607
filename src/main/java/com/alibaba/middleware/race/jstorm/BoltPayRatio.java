package com.alibaba.middleware.race.jstorm;

import backtype.storm.Constants;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import clojure.lang.Atom;

import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.middleware.race.RaceConstant;
import com.alibaba.middleware.race.Tair.TairOperatorImpl;
import com.alibaba.middleware.race.model.TableItemFactory;
import com.google.common.util.concurrent.AtomicDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by kevin on 16-6-26.
 */
public class BoltPayRatio implements IRichBolt {
    private static final long serialVersionUID = -1910650485341329191L;
    private static Logger LOG = LoggerFactory.getLogger(BoltPayRatio.class);

    private Map<Long, AtomicDouble> wirelessMap = new HashMap<>();
    private Map<Long, AtomicDouble> pcMap = new HashMap<>();
    private double lastTimestamp = -1;
    private double maxTimestamp = -1;
    private Set<Long> alterTimeSet = new HashSet<>();


    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
    }

    @Override
    public void execute(Tuple tuple) {
        String componentId = tuple.getSourceComponent();
        String streamId = tuple.getSourceStreamId();

        if (RaceConstant.STREAM_STOP.equals(streamId)) {
            // 停止信号
            LOG.info("stop signal");
        } else if (Constants.SYSTEM_COMPONENT_ID.equals(componentId) && Constants.SYSTEM_TICK_STREAM_ID.equals(streamId)){
            // tick stream singal
            // 开始写数据
            if (!alterTimeSet.isEmpty()) {
                for (Long time : alterTimeSet) {
                    AtomicDouble wirelessPrice = wirelessMap.get(time);
                    AtomicDouble pcPrice = pcMap.get(time);
                    if (wirelessPrice != null && pcPrice != null) {
                        double ratio = TableItemFactory.round(wirelessPrice.doubleValue() / pcPrice.doubleValue(), 2);
                        TairOperatorImpl.getInstance().write(RaceConfig.prex_ratio + time, ratio);
                    }
                }
                alterTimeSet.clear();
            }

        }
        else if (streamId.equals(RaceConstant.STREAM_PAY_PLATFORM)) {
            //            long orderID = tuple.getLong(0);
            short platform = tuple.getShort(1);
            long timestamp = tuple.getLong(2);
            double price = tuple.getDouble(3);
            if (platform == 0) { // PC
                if (timestamp < maxTimestamp) {
                    // 该tuple的时间小于最大时间，出现了乱序
                    // 可以忽略
                } else if(timestamp == maxTimestamp) {
                    // tuple的时间是正在处理的时间，该tuple属于当前的时间
                    AtomicDouble oldValue = pcMap.get(timestamp);
                    if (oldValue == null) {
                        oldValue = new AtomicDouble(price);
                    } else {
                        oldValue.addAndGet(price);
                    }
                    pcMap.put(timestamp, oldValue);
                    alterTimeSet.add(timestamp);
                } else {
                    // tuple的时间 大于最大时间，新的一分钟的数据出现了
                    lastTimestamp = maxTimestamp;
                    maxTimestamp = timestamp;
                    AtomicDouble lastPrice = pcMap.get(lastTimestamp);
                    if (lastPrice != null) {
                        pcMap.put(timestamp, new AtomicDouble(price + lastPrice.doubleValue()));
                    } else {
                        pcMap.put(timestamp, new AtomicDouble(price));
                    }
                }
            } else { // 无线
                if (timestamp < maxTimestamp) {
                    // 该tuple的时间小于最大时间
                } else if(timestamp == maxTimestamp) {
                    // tuple的时间是正在处理的时间，该tuple属于当前的时间
                    AtomicDouble oldValue = wirelessMap.get(timestamp);
                    if (oldValue == null) {
                        oldValue = new AtomicDouble(price);
                    } else {
                        oldValue.addAndGet(price);
                    }
                    wirelessMap.put(timestamp, oldValue);
                    alterTimeSet.add(timestamp);
                } else {
                    // tuple的时间 大于最大时间，新的一分钟的数据出现了
                    lastTimestamp = maxTimestamp;
                    maxTimestamp = timestamp;
                    AtomicDouble lastPrice = wirelessMap.get(lastTimestamp);
                    if (lastPrice != null) {
                        wirelessMap.put(timestamp, new AtomicDouble(price + lastPrice.doubleValue()));
                    } else {
                        wirelessMap.put(timestamp, new AtomicDouble(price));
                    }
                }
            }
        }
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }

}
