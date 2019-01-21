package com.neighborhood.aka.laplace.estuary.mysql.lifecycle.reborn.batch.mappings

import com.alibaba.otter.canal.protocol.CanalEntry.{EventType, RowData}
import com.neighborhood.aka.laplace.estuary.bean.key.PartitionStrategy
import com.neighborhood.aka.laplace.estuary.mysql.lifecycle
import com.neighborhood.aka.laplace.estuary.mysql.lifecycle.{BinlogPositionInfo, MysqlRowDataInfo}
import com.neighborhood.aka.laplace.estuary.mysql.schema.storage.MysqlSchemaHandler
import com.neighborhood.aka.laplace.estuary.mysql.utils.CanalEntryJsonHelper
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer

/**
  * Created by john_liu on 2019/1/13.
  *
  * @author neighborhood.aka.laplace
  * @param partitionStrategy   分区策略
  * @param syncTaskId          同步任务Id
  * @param syncStartTime       同步任务开始时间
  * @param mysqlSchemaHandler  mysqlSchema处理器
  * @param schemaComponentIsOn 是否开启schema管理
  * @param config              typesafe.config
  * @param tableMappingRule    表名称映射规则
  */
final class CanalEntry2RowDataInfoMappingFormat4Sda(
                                                     override val partitionStrategy: PartitionStrategy,
                                                     override val syncTaskId: String,
                                                     override val syncStartTime: Long,
                                                     override val mysqlSchemaHandler: MysqlSchemaHandler,
                                                     override val schemaComponentIsOn: Boolean,
                                                     override val config: Config,
                                                     val tableMappingRule: Map[String, String]
                                                   ) extends CanalEntryMappingFormat[MysqlRowDataInfo] {


  override def transform(x: lifecycle.EntryKeyClassifier): MysqlRowDataInfo = {
    val entry = x.entry
    val originalTableName = entry.getHeader.getTableName
    val originalDbName = entry.getHeader.getSchemaName
    val (dbName, tableName) = getSdaDbNameAndTableName(originalDbName, originalTableName)
    val sql: String = entry.getHeader.getEventType
    match {
      case EventType.INSERT | EventType.UPDATE => handleUpdateEventRowDataToSql(dbName, tableName, x.rowData)
      case EventType.DELETE => handleDeleteEventRowDataToSql(dbName, tableName, x.rowData)
    }
    val binlogPositionInfo = BinlogPositionInfo(
      entry.getHeader.getLogfileName,
      entry.getHeader.getLogfileOffset,
      entry.getHeader.getExecuteTime
    )
    MysqlRowDataInfo(x.entry.getHeader.getSchemaName, x.entry.getHeader.getTableName, x.entry.getHeader.getEventType, x.rowData, binlogPositionInfo, Option(sql))
  }

  /**
    * 处理Mysql的UpdateEvent和InsertEvent
    *
    * @param dbName    数据库名称
    * @param tableName 表名称
    * @param rowData   rowData
    * @return Sql
    */
  private def handleUpdateEventRowDataToSql(dbName: String, tableName: String, rowData: RowData): String = {
    val values = new ListBuffer[String]
    val fields = new ListBuffer[String]
    (0 until rowData.getAfterColumnsCount).foreach {
      index =>
        val column = rowData.getAfterColumns(index)
        if (column.hasValue) {
          values.append(CanalEntryJsonHelper.getSqlValueByMysqlType(column.getMysqlType, column.getValue))
          fields.append(column.getName)
        }
    }
    s"replace into $dbName.$tableName(${fields.mkString(",")}) VALUES (${values.mkString(",")}) "
  }

  /**
    * 处理Mysql的Delete事件
    *
    * @param dbName    数据库名称
    * @param tableName 表名称
    * @param rowData   rowData
    * @return sql
    */

  private def handleDeleteEventRowDataToSql(dbName: String, tableName: String, rowData: RowData): String = {
    import scala.collection.JavaConverters._
    val columnList = rowData.getAfterColumnsList.asScala
    columnList.find(x => x.hasIsKey && x.getIsKey).fold { //暂时不处理无主键的
      ""
    } {
      keyColumn =>
        val keyName = keyColumn.getName
        val keyValue = CanalEntryJsonHelper.getSqlValueByMysqlType(keyColumn.getMysqlType, keyColumn.getValue)
        s"DELETE FROM $dbName.$tableName WHERE $keyName=$keyValue"
    }
  }

  /**
    * 获得Sda表名称
    *
    * @param dbName    原始数据库名称
    * @param tableName 原始表名称
    * @return sda库表名称
    */
  private def getSdaDbNameAndTableName(dbName: String, tableName: String): (String, String) = {
    (dbName, tableMappingRule(tableName))
  }

}
