package com.alibaba.middleware.race.jstorm;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import com.alibaba.middleware.race.RaceConstant;
import com.alibaba.middleware.race.Tair.TairOperatorImpl;
import com.google.common.util.concurrent.AtomicDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by kevin on 16-6-26.
 */
public class BoltPayRatio implements IRichBolt {
    private static final long serialVersionUID = -1910650485341329191L;
    private static Logger LOG = LoggerFactory.getLogger(BoltPayRatio.class);

    private HashMap<Long, AtomicDouble> wirelessMap = new HashMap<>();
    private HashMap<Long, AtomicDouble> pcMap = new HashMap<>();
    private long timestamp = 0L;
    private short flag = 0;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
    }

    @Override
    public void execute(Tuple tuple) {
        String streamId = tuple.getSourceStreamId();
        if (streamId.equals(RaceConstant.STREAM_STOP)) {
            double res = round(wirelessMap.get(this.timestamp).doubleValue() / pcMap.get(this.timestamp).doubleValue(), 2);
//            TairOperatorImpl.getInstance().write(this.timestamp, res);
            LOG.info("### streamId {} got the end signal!");
        } else if (streamId.equals(RaceConstant.STREAM_PAY_PLATFORM)) {
//            long orderID = tuple.getLong(0);
            short platform = tuple.getShort(1);
            long timestamp = tuple.getLong(2);
            double price = tuple.getDouble(3);

            if (this.timestamp == 0) {
                this.timestamp = timestamp;
                return;
            }

            if (platform == 0) {    // PC
                AtomicDouble pcPrice = pcMap.get(timestamp);
                if (pcPrice == null) {
                    AtomicDouble beforSum = pcMap.get(timestamp - 60L);
                    double befor = 0.0d;
                    if (beforSum != null) {
                        befor = beforSum.doubleValue();
                    }
                    pcPrice = new AtomicDouble(befor);
                }
                pcPrice.addAndGet(price);
                pcMap.put(timestamp, pcPrice);
            } else {    // 无线
                AtomicDouble wirelessPrice = wirelessMap.get(timestamp);
                if (wirelessPrice == null) {
                    AtomicDouble beforSum = wirelessMap.get(timestamp - 60L);
                    double befor = 0.0d;
                    if (beforSum != null) {
                        befor = beforSum.doubleValue();
                    }
                    wirelessPrice = new AtomicDouble(befor);
                }
                wirelessPrice.addAndGet(price);
                wirelessMap.put(timestamp, wirelessPrice);
            }

            if (this.timestamp < timestamp) {
                AtomicDouble tmpWireless = wirelessMap.get(this.timestamp);
                AtomicDouble tmpPc = pcMap.get(this.timestamp);
                if (tmpPc == null || tmpWireless == null) {
                    return;
                }
                double res = round(tmpWireless.doubleValue() / tmpPc.doubleValue(), 2);
//                TairOperatorImpl.getInstance().write(this.timestamp, res);
                LOG.info(">>> ratio {} : {}", this.timestamp, res);
                this.timestamp = timestamp;
            } else if (this.timestamp > timestamp) {
                // TODO 此处添加补充小部分乱序的处理逻辑
                flag = platform;

                while (timestamp <= this.timestamp) {
                    AtomicDouble atoWireless = wirelessMap.get(timestamp);
                    AtomicDouble atoPc = pcMap.get(timestamp);
                    if (atoWireless == null || atoPc == null) {
                        return;
                    }
                    double wireless = atoWireless.doubleValue();
                    double pc = atoPc.doubleValue();

                    double res = round(wireless / pc, 2);
//                    TairOperatorImpl.getInstance().write(timestamp, res);
                    LOG.info(">>> new ratio {} : {}", this.timestamp, res);

                    timestamp += 60L;
                    if (flag == 0) { // PC
                        atoWireless.addAndGet(price);
                        wirelessMap.put(timestamp, atoWireless);
                    } else if (flag == 1) { // 无线
                        atoPc.addAndGet(price);
                        pcMap.put(timestamp, atoPc);
                    }
                }
            }
        }
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    @Override
    public void cleanup() {
        // TODO 关闭前将最后的结果写入 tair 中
        double res = round(wirelessMap.get(this.timestamp).doubleValue() / pcMap.get(this.timestamp).doubleValue(), 2);
//        TairOperatorImpl.getInstance().write(this.timestamp, res);
        LOG.info(">>> cleanup ratio {} : {}", this.timestamp, res);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }
}
