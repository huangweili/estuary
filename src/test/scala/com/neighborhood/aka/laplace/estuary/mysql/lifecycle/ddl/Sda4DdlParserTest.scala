package com.neighborhood.aka.laplace.estuary.mysql.lifecycle.ddl

import com.neighborhood.aka.laplace.estuary.UnitSpec
import com.neighborhood.aka.laplace.estuary.mysql.schema.defs.ddl.{AddColumnMod, ChangeColumnMod, RemoveColumnMod, TableAlter}
import com.neighborhood.aka.laplace.estuary.mysql.schema.{Parser, SdaSchemaMappingRule}
import com.neighborhood.aka.laplace.estuary.mysql.schema.Parser.SchemaChangeToDdlSqlSyntax

/**
  * Created by john_liu on 2019/2/14.
  */
class Sda4DdlParserTest extends UnitSpec {

  val mappingRuleMap = Map("a.a" -> "a_map.a_map", "b.b" -> "b_map.b_map")
  val schemaMappingRule = new SdaSchemaMappingRule(mappingRuleMap)
  val alterTable1 = "ALTER TABLE a.a ADD col1 text DEFAULT 'hello';"
  val alterTable2 = "ALTER TABLE `a`.`a` ADD column `col1` int(11) comment 'c' AFTER `afterCol`"
  val alterTable3 = "ALTER TABLE `a`.`a` ADD column `col1` int(11) unsigned DEFAULT '1' comment 'c' AFTER `afterCol`"
  val alterTable4 = "alter table `a`.`a` add column `c_double` double(10,2) unsigned not null Default '1.00' COMMENT 'c' AFTER `afterCol`"
  val alterTable5 = "alter table `a`.`a` add column `c_decimal` decimal(10,2) unsigned not null Default '1.00' COMMENT 'c' AFTER `afterCol`"
  val alterTable6 = "alter table `a`.`a` drop column `drop`"
  val alterTable7 = "alter table `a`.`a` CHANGE column `foo` bar varchar(255)  default 'foo' not null comment 'c'"
  val alterTable8 = "alter table `a`.`a` MODIFY column bar varchar(255)  default 'foo' not null comment 'c'"

  "test 1" should "successfully handle Alter table with column add" in {
    val schemaChange = Parser.parseAndReplace(alterTable1, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val addColumnMod = tableAlter.columnMods.get(0).asInstanceOf[AddColumnMod]
    assert(addColumnMod.definition.getName == "col1")
    assert(addColumnMod.definition.getType == "text")
    assert(addColumnMod.definition.getDefaultValue == "'hello'")
    val ddl = schemaChange.toDdlSql
    assert(ddl == "ALTER TABLE a_map.a_map ADD col1 text  DEFAULT 'hello'")
  }

  "test 2" should "successfully handle Alter table with column add" in {
    val schemaChange = Parser.parseAndReplace(alterTable2, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val addColumnMod = tableAlter.columnMods.get(0).asInstanceOf[AddColumnMod]
    assert(addColumnMod.definition.getName == "col1")
    assert(addColumnMod.definition.getType == "int")
    assert(addColumnMod.definition.getDefaultValue == null)
    assert(addColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map ADD COLUMN col1 int")
  }

  "test 3" should "successfully handle Alter table with column add" in {
    val schemaChange = Parser.parseAndReplace(alterTable3, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val addColumnMod = tableAlter.columnMods.get(0).asInstanceOf[AddColumnMod]
    assert(addColumnMod.definition.getName == "col1")
    assert(addColumnMod.definition.getType == "int")
    assert(addColumnMod.definition.getDefaultValue == "'1'")
    assert(addColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map ADD COLUMN col1 int unsigned DEFAULT '1'")
  }

  "test 4" should "successfully handle Alter table with column add" in {
    val schemaChange = Parser.parseAndReplace(alterTable4, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val addColumnMod = tableAlter.columnMods.get(0).asInstanceOf[AddColumnMod]
    assert(addColumnMod.definition.getName == "c_double")
    assert(addColumnMod.definition.getType == "double")
    assert(addColumnMod.definition.getFullType == "double(10,2)")
    assert(addColumnMod.definition.getDefaultValue == "'1.00'")
    assert(addColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map ADD COLUMN c_double double(10,2)  DEFAULT '1.00'")
  }

  "test 5" should "successfully handle Alter table with column add" in {
    val schemaChange = Parser.parseAndReplace(alterTable5, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val addColumnMod = tableAlter.columnMods.get(0).asInstanceOf[AddColumnMod]
    assert(addColumnMod.definition.getName == "c_decimal")
    assert(addColumnMod.definition.getType == "decimal")
    assert(addColumnMod.definition.getFullType == "decimal(10,2)")
    assert(addColumnMod.definition.getDefaultValue == "'1.00'")
    assert(addColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map ADD COLUMN c_decimal decimal(10,2)  DEFAULT '1.00'")
  }

  "test 6" should "successfully handle table alter with column drop" in {
    val schemaChange = Parser.parseAndReplace(alterTable6, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val removeColumnMod = tableAlter.columnMods.get(0).asInstanceOf[RemoveColumnMod]
    assert(removeColumnMod.name == "drop")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map DROP COLUMN drop")
  }

  "test 7" should "successfully handle table alter with column change" in {
    val schemaChange = Parser.parseAndReplace(alterTable7, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val changeColumnMod = tableAlter.columnMods.get(0).asInstanceOf[ChangeColumnMod]
    assert(changeColumnMod.name == "foo")
    assert(changeColumnMod.definition.getName == "bar")
    assert(changeColumnMod.definition.getType == "varchar")
    assert(changeColumnMod.definition.getFullType == "varchar")
    assert(changeColumnMod.definition.getDefaultValue == "'foo'")
    assert(changeColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map CHANGE COLUMN foo bar varchar  DEFAULT 'foo'")
  }

  "test 8" should "successfully handle table alter with column change" in {
    val schemaChange = Parser.parseAndReplace(alterTable8, "a_map", schemaMappingRule)
    assert(schemaChange.isInstanceOf[TableAlter])
    val tableAlter = schemaChange.asInstanceOf[TableAlter]
    assert(tableAlter.database == "a_map")
    assert(tableAlter.table == "a_map")
    assert(tableAlter.newDatabase == "a_map")
    assert(tableAlter.newTableName == "a_map")
    val changeColumnMod = tableAlter.columnMods.get(0).asInstanceOf[ChangeColumnMod]
    assert(changeColumnMod.name == "bar")
    assert(changeColumnMod.definition.getName == "bar")
    assert(changeColumnMod.definition.getType == "varchar")
    assert(changeColumnMod.definition.getFullType == "varchar")
    assert(changeColumnMod.definition.getDefaultValue == "'foo'")
    assert(changeColumnMod.definition.getComment == "c")
    val ddl = schemaChange.toDdlSql
    assert(ddl.trim == "ALTER TABLE a_map.a_map MODIFY COLUMN   bar varchar  DEFAULT 'foo'")
  }
}