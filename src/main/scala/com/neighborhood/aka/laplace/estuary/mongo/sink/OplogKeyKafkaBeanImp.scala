package com.neighborhood.aka.laplace.estuary.mongo.sink

import com.neighborhood.aka.laplace.estuary.bean.datasink.KafkaBean
import com.neighborhood.aka.laplace.estuary.bean.key.{BinlogKey, JsonKeySerializer, MultipleJsonKeyPartitioner, OplogKey}
import org.apache.kafka.common.serialization.StringSerializer

/**
  * Created by john_liu on 2019/2/28.
  */
final case class OplogKeyKafkaBeanImp(
                                       override val bootstrapServers: String,

                                       /**
                                         * defaultTopic
                                         */
                                       override val topic: String,

                                       /**
                                         * ddl专用Topic
                                         */
                                       override val ddlTopic: String

                                     )(
                                       override val specificTopics: Map[String, String] = Map.empty,

                                       /**
                                         * 分区类
                                         */
                                       override val partitionerClass: String = classOf[MultipleJsonKeyPartitioner].getName,

                                       /**
                                         * key Serializer类
                                         */
                                       override val keySerializer: String = classOf[JsonKeySerializer].getName,

                                       /**
                                         * value Serializer类
                                         */
                                       override val valueSerializer: String = classOf[StringSerializer].getName) extends KafkaBean[OplogKey, String]