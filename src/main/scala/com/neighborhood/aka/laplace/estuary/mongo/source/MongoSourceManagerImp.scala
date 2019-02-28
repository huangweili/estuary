package com.neighborhood.aka.laplace.estuary.mongo.source

import com.neighborhood.aka.laplace.estuary.bean.resource.{DataSourceBase, MongoSourceBean}
import com.neighborhood.aka.laplace.estuary.core.task.SourceManager

/**
  * Created by john_liu on 2019/2/28.
  */
trait MongoSourceManagerImp extends SourceManager[MongoConnection] {
  override def sourceBean: MongoSourceBean

  override def buildSource: MongoConnection = {
    new MongoConnection(sourceBean)
  }
}
