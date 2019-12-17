package com.databricks.spark.sql.perf
import com.databricks.spark.sql.perf.tpcds.TPCDSTables


/**
  * create with com.databricks.spark.sql.perf
  * USER: husterfox
  */
object MyBenchMark extends Benchmark {


  // Set:
  val rootDir = "" // root directory of location to create data in.
  val databaseName = "" // name of database to create.
  val scaleFactor = "" // scaleFactor defines the size of the dataset to generate (in GB).
  val format = "" // valid spark format like parquet "parquet".

  // Run:
  val tables = new TPCDSTables(sqlContext,
    dsdgenDir = "/tmp/tpcds-kit/tools", // location of dsdgen
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
    tableFilter = "", // "" means generate all tables
    numPartitions = 100) // how many dsdgen partitions to run - number of input tasks.


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
