package com.alibaba.middleware.race.jstorm;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.alibaba.jstorm.utils.JStormUtils;
import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.middleware.race.RaceConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 这是一个很简单的例子
 * 选手的拓扑提交到集群，我们是有超时设置的。每个选手的拓扑最多跑20分钟，一旦超过这个时间
 * 我们会将选手拓扑杀掉。
 */

/**
 * 选手拓扑入口类，我们定义必须是com.alibaba.middleware.race.jstorm.RaceTopology
 * 因为我们后台对选手的git进行下载打包，拓扑运行的入口类默认是com.alibaba.middleware.race.jstorm.RaceTopology；
 * 所以这个主类路径一定要正确
 */
public class RaceTopology {
    private static Logger LOG = LoggerFactory.getLogger(RaceTopology.class);

    public final static String TOPOLOGY_SPOUT_PARALLELISM_HINT = "spout.parallel";
    public final static String TOPOLOGY_BOLT_PARALLELISM_HINT = "bolt.parallel";

    public static void main(String[] args) {
        /*
         * 全局config，局部配置在每个组件的 getComponentConfiguration() 方法中定义
         * 优先级：局部 > 全局
         */
        Config conf = new Config();

//        LocalCluster cluster = new LocalCluster();
//        cluster.submitTopology(RaceConfig.JstormTopologyName, conf, builtTopology(conf).createTopology());
//        本地调试设定运行时间
//        Utils.sleep(30000);
//        cluster.killTopology(topologyName);
//        cluster.shutdown();

//        TODO 打包上传需注释上面 LocalCluster 部分，开启下面部分；同时 pom 包要开启 jstorm 的 provided
        try {
            StormSubmitter.submitTopology(RaceConfig.JstormTopologyName, conf, builtTopology(conf).createTopology());
        } catch (AlreadyAliveException | InvalidTopologyException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
    }

    // 正式逻辑在这里组织
    private static TopologyBuilder builtTopology(Config conf) {
        int spout_Parallelism_hint = JStormUtils.parseInt(conf.get(TOPOLOGY_SPOUT_PARALLELISM_HINT), 1);
        int split_Parallelism_hint = JStormUtils.parseInt(conf.get(TOPOLOGY_BOLT_PARALLELISM_HINT), 1);
        int count_Parallelism_hint = JStormUtils.parseInt(conf.get(TOPOLOGY_BOLT_PARALLELISM_HINT), 2);
        int result_Parallelism_hint = JStormUtils.parseInt(conf.get(TOPOLOGY_BOLT_PARALLELISM_HINT), 1);

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(RaceConstant.ID_SPOUT_SOURCE, new SpoutRocketMq(), spout_Parallelism_hint);

        //  从rocketMQ中拉取数据
        builder.setBolt(RaceConstant.ID_SPLIT_PLATFORM, new BoltSplitStream(), split_Parallelism_hint)
                .fieldsGrouping(RaceConstant.ID_SPOUT_SOURCE,
                        new Fields(RaceConstant.FIELD_SOURCE_DATA));

        // PC端/无线端 支付数据分析
        builder.setBolt(RaceConstant.ID_PAY_RATIO, new BoltPayRatio(), result_Parallelism_hint)
                .fieldsGrouping(RaceConstant.ID_SPLIT_PLATFORM,
                        RaceConstant.STREAM_PAY_PLATFORM,
                        new Fields(RaceConstant.payTime));
        return builder;
    }
}