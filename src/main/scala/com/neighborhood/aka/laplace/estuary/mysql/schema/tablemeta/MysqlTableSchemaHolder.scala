package com.neighborhood.aka.laplace.estuary.mysql.schema.tablemeta

import com.neighborhood.aka.laplace.estuary.core.sink.mysql.MysqlSinkFunc
import com.neighborhood.aka.laplace.estuary.core.util.JavaCommonUtil
import com.neighborhood.aka.laplace.estuary.mysql.schema.defs.ddl._
import com.neighborhood.aka.laplace.estuary.mysql.schema.tablemeta.MysqlTableSchemaHolder._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by john_liu on 2019/1/23.
  *
  * mysql tables的元数据信息
  *
  * @author neighborhood.aka.laplace
  */
final class MysqlTableSchemaHolder(
                                    @volatile private var tableSchemas: Map[String, EstuaryMysqlTableMeta]
                                  ) {

  /**
    * 获取tableMeta
    *
    * @param fullName $databaseName.$tableName
    * @return 找到Some(x) else None
    */
  def getTableMetaByFullName(fullName: String): Option[EstuaryMysqlTableMeta] = tableSchemas.get(fullName)

  /**
    * 更新tableMeta信息
    *
    * @param schemaChange
    */
  def updateTableMeta(schemaChange: SchemaChange): Unit = {
    schemaChange match {
      case tableAlter: TableAlter => handleTableAlter(tableAlter)
      case tableCreate: TableCreate => handleTableCreate(tableCreate)
      case tableTruncate: TableTruncate => //do nothing
      case tableDrop: TableDrop => handleTableDrop(tableDrop)
      case _ => //do nothing
    }

  }

  /**
    * 更新tableMeta信息
    *
    * @param schemaChange
    */
  def updateTableMeta(schemaChange: SchemaChange, sinkFunc: MysqlSinkFunc): Unit = {
    schemaChange match {
      case tableAlter: TableAlter => handleTableAlter(tableAlter)
      case tableCreate: TableCreate => handleTableCreate(tableCreate)
      case tableTruncate: TableTruncate => //do nothing
      case tableDrop: TableDrop => handleTableDrop(tableDrop)
      case _ => //do nothing
    }
  }

  /**
    * 处理tableDrop
    *
    * @param drop
    */
  private def handleTableDrop(drop: TableDrop): Unit

  = {
    tableSchemas = tableSchemas.filterNot(x => x._1 == s"${drop.database}.${drop.table}")
  }

  /**
    * 处理创建表
    * 支持like 语句
    * 如果Schema 缓存中存在 key ，则不更新
    *
    * @param create
    */
  private def handleTableCreate(create: TableCreate, sinkFunc: Option[MysqlSinkFunc] = None): Unit

  = {
    val tableName = create.table
    val dbName = create.database
    val key = s"$dbName.$tableName"
    lazy val tableSchemaFromLikeTable = tableSchemas(s"$dbName.${create.likeTable}").copy(tableName = tableName)
    if (!tableSchemas.contains(key)) {
      if (!JavaCommonUtil.isEmpty(create.likeTable)) tableSchemas = tableSchemas + (key -> tableSchemaFromLikeTable)
      else {
        val columnInfoList = create.columns.asScala.map(_.toEstuaryMysqlColumnInfo).toList
        val createTable = sinkFunc.flatMap(sink => getCreateTableSql(dbName, tableName, sink))
        tableSchemas = tableSchemas.updated(key, EstuaryMysqlTableMeta(dbName, tableName, columnInfoList, createTable))
      }
    }
  }

  /**
    * 处理Alter/Rename
    *
    * @param alter TableAlter
    */
  private def handleTableAlter(alter: TableAlter, sinkFunc: Option[MysqlSinkFunc] = None): Unit

  = {
    val newDatabaseName = Option(alter.newDatabase).getOrElse(alter.database)
    val newTableName = Option(alter.newTableName).getOrElse(alter.table)
    val key = s"${newDatabaseName}.${newTableName}"
    val oldColumns = tableSchemas(key).columns
    val mods = Try(alter.columnMods.asScala.toList).getOrElse(List.empty)

    @tailrec
    def loopBuild(mods: List[ColumnMod] = mods, acc: List[EstuaryMysqlColumnInfo] = oldColumns): List[EstuaryMysqlColumnInfo] = {
      mods match {
        case hd :: tl => hd match {
          case add: AddColumnMod => loopBuild(tl, add.definition.toEstuaryMysqlColumnInfo :: acc)
          case remove: RemoveColumnMod => loopBuild(tl, acc.filterNot(x => x.name == remove.name))
          case change: ChangeColumnMod => loopBuild(
            tl, acc.map { column => if (column.name == change.name) change.definition.toEstuaryMysqlColumnInfo else column })
        }
        case Nil => acc
      }

    }

    val createTableSql = sinkFunc.flatMap(sink => getCreateTableSql(alter.newDatabase, alter.newTableName, sink))
    tableSchemas = tableSchemas.updated(key, EstuaryMysqlTableMeta(alter.newDatabase, alter.newTableName, loopBuild(), createTableSql))
  }

}

object MysqlTableSchemaHolder {

  def getTableSchemasByDbName(dbs: List[String], sink: MysqlSinkFunc): List[Map[String, AnyRef]] = {
    val queryCondition = dbs.map(x => s"'$x'").mkString(",")
    val sql = s"select a.TABLE_SCHEMA, a.TABLE_NAME,b.COLUMN_NAME,b.DATA_TYPE,b.ORDINAL_POSITION from INFORMATION_SCHEMA.TABLES a join INFORMATION_SCHEMA.COLUMNS b ON (a.TABLE_NAME = b.TABLE_NAME) where a.TABLE_SCHEMA in ( $queryCondition )"
    sink.queryAsScalaList(sql)
  }


  def getCreateTableSql(dbName: String, tableName: String, sink: MysqlSinkFunc): Option[String] = {
    Try(sink.queryAsScalaList(s"show create table $dbName.$tableName")(0)).toOption.flatMap {
      map =>
        map.filter {
          _._1.toLowerCase.contains("create")
        }.toList.headOption.map(_.toString())
    }
  }
}
