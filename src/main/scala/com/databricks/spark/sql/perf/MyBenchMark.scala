package com.databricks.spark.sql.perf

import java.text.SimpleDateFormat
import java.util.Date

import com.databricks.spark.sql.perf.tpcds.TPCDSTables
import org.apache.spark.sql.SparkSession


/**
  * create with com.databricks.spark.sql.perf
  * USER: husterfox
  */
case class CreateTableConifg(
                              filter: String = "",
                              rootDir: String = "",
                              location: String = "",
                              databaseName: String = "tpcds",
                              scaleFactor: String = "1",
                              hive: Boolean = true,
                              format: String = "parquet",
                              partitionNum: Int = 2)

object MyBenchMark {
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[CreateTableConifg]("spark-sql-perf MybenchMark") {
      head("spark-sql-perf MyBenchMark Create Table ", "0.2.0")
      opt[String]('t', "filter")
        .action((x, c) => c.copy(filter = x))
        .text("\"\" means generate all tables")
      opt[String]('r', "rootdir")
        .action((x, c) => c.copy(rootDir = x))
        .required()
        .text("<required> root directory of location to create data in ")
      opt[String]('l', "location")
        .action((x, c) => c.copy(location = x))
        .required()
        .text("<required>  location of dsdgen ")
      opt[String]('d', "db")
        .action((x, c) => c.copy(databaseName = x))
        .text("name of database to create")
      opt[String]('s', "scalefactor")
        .action((x, c) => c.copy(scaleFactor = x))
        .text("scaleFactor defines the size of the dataset to generate (in GB)")
      opt[Boolean]('h', "hive")
        .action((x, c) => c.copy(hive = x))
        .text("enable Spark hive support")
      opt[String]('f', "format")
        .action((x, c) => c.copy(format = x))
        .text("valid spark format like parquet \"parquet\"")
      opt[Int]('p', "partition")
        .action((x, c) => c.copy(partitionNum = x))
        .text("how many dsdgen partitions to run - number of input tasks")
      help("help")
        .text("prints this usage text")
    }

    parser.parse(args, CreateTableConifg()) match {
      case Some(config) =>
        run(config)
      case None =>
        parser.showUsage()
        System.exit(1)
    }

  }

  def run(config: CreateTableConifg): Unit = {
    val fm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val tim = fm.format(new Date(System.currentTimeMillis()))
    val appName = getClass.getSimpleName + tim

    val spark = if (config.hive) {
      SparkSession.builder().enableHiveSupport().appName(appName).getOrCreate()
    } else {
      SparkSession.builder().appName(appName).getOrCreate()
    }
    val sqlContext = spark.sqlContext
    // Set:
    val rootDir = config.rootDir // root directory of location to create data in.
    val databaseName = config.databaseName // name of database to create.
    val scaleFactor = config.scaleFactor // scaleFactor defines the size of the dataset to generate (in GB).
    val format = config.format // valid spark format like parquet "parquet".

    // Run:
    val tables = new TPCDSTables(sqlContext,
      dsdgenDir = config.location, // location of dsdgen
      scaleFactor = scaleFactor,
      useDoubleForDecimal = false, // true to replace DecimalType with DoubleType
      useStringForDate = false) // true to replace DateType with StringType


    val sql = sqlContext.sparkSession.sql _

    tables.genData(
      location = rootDir,
      format = format,
      overwrite = true, // overwrite the data that is already there
      partitionTables = true, // create the partitioned fact tables
      clusterByPartitionColumns = true, // shuffle to get partitions coalesced into single files.
      filterOutNullPartitionValues = false, // true to filter out the partition with NULL key value
      tableFilter = config.filter, // "" means generate all tables
      numPartitions = 2) // how many dsdgen partitions to run - number of input tasks.


    // Create the specified database
    sql(s"create database $databaseName")
    // Create metastore tables in a specified database for your data.
    // Once tables are created, the current database will be switched to the specified database.
    tables.createExternalTables(rootDir, "parquet", databaseName, overwrite = true, discoverPartitions = true)
    // Or, if you want to create temporary tables
    // tables.createTemporaryTables(location, format)

    // For CBO only, gather statistics on all columns:
    tables.analyzeTables(databaseName, analyzeColumns = true)

  }
}
