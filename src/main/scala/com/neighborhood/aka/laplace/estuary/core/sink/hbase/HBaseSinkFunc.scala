package com.neighborhood.aka.laplace.estuary.core.sink.hbase

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

import com.neighborhood.aka.laplace.estuary.bean.datasink.HBaseBean
import com.neighborhood.aka.laplace.estuary.core.sink.SinkFunc
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._

/**
  * Created by john_liu on 2019/3/14.
  */
abstract class HBaseSinkFunc(val hbaseSinkBean: HBaseBean) extends SinkFunc {


  lazy val conn = initConnection

  private val connectionStatus: AtomicBoolean = new AtomicBoolean(false)


  private def initConnection: Connection = {

    ???
  }

  def start(): Unit = {
    conn
    assert(conn != null)
    connectionStatus.set(true)
  }

  override def close: Unit = {
    if (connectionStatus.compareAndSet(true, false)) conn.close()
  }

  def getTable(tableName: String)(implicit pool: ExecutorService): HTable = {
    if (!connectionStatus.get()) throw new IllegalStateException()
    new HTable(TableName.valueOf(tableName), conn, pool)
  }
}