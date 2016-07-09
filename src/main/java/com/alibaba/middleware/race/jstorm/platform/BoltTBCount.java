package com.alibaba.middleware.race.jstorm.platform;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.middleware.race.RaceConstant;
import com.alibaba.middleware.race.Tair.TairOperatorImpl;
import com.google.common.util.concurrent.AtomicDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by kevin on 16-7-8.
 */
public class BoltTBCount implements IRichBolt {
    private static final long serialVersionUID = -8531647739679708927L;
    private static Logger LOG = LoggerFactory.getLogger(BoltTBCount.class);

    private Map<Long, AtomicDouble> tbMap = new HashMap<>();
    private long cur_timestamp = 0L;
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {

    }

    @Override
    public void execute(Tuple tuple) {
        String streamId = tuple.getSourceStreamId();
        if (streamId.equals(RaceConstant.STREAM_STOP)) {
            AtomicDouble total = tbMap.get(cur_timestamp);
            if (total == null) return;
            TairOperatorImpl.getInstance().write(
                    RaceConfig.prex_taobao + cur_timestamp, total.doubleValue());
            return;
        }
        long timestamp = tuple.getLongByField(RaceConstant.payTime);
        double price = tuple.getDoubleByField(RaceConstant.payAmount);
        if (cur_timestamp == 0) {
            cur_timestamp = timestamp;
        }
        AtomicDouble total = tbMap.get(timestamp);
        if (total == null) {
            total = new AtomicDouble(0.0);
        }
        total.addAndGet(price);
        tbMap.put(timestamp, total);
        if (cur_timestamp < timestamp) {
            AtomicDouble res = tbMap.get(cur_timestamp);
            if (res == null) {
                return;
            }
            TairOperatorImpl.getInstance().write(RaceConfig.prex_taobao + cur_timestamp, res.doubleValue());
//            LOG.info("### {}:{}", RaceConfig.prex_taobao + cur_timestamp, res.doubleValue());

            cur_timestamp = timestamp;

        } else if (cur_timestamp > timestamp) {
            AtomicDouble new_res = tbMap.get(timestamp);
            if (new_res == null) {
                new_res = new AtomicDouble(0.0);
                new_res.addAndGet(price);
                tbMap.put(timestamp, new_res);
            }
            TairOperatorImpl.getInstance().write(RaceConfig.prex_taobao + timestamp, new_res.doubleValue());
//            LOG.info("### {}:{}", RaceConfig.prex_taobao + cur_timestamp, new_res.doubleValue());
        }
    }

    @Override
    public void cleanup() {
        AtomicDouble total = tbMap.get(cur_timestamp);
        if (total == null) return;
        TairOperatorImpl.getInstance().write(
                RaceConfig.prex_taobao + cur_timestamp, total.doubleValue());

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }
}